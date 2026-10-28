# 已知缺陷 0001：场地预约并发超卖

> 发现日期：2026-09-17 · 首次修复验证：2026-09-24 · 状态：**个人预约、活动占场及双球场交错已通过对应回归；更多交错未覆盖** · 严重级：**高**
> 复现探针：`src/test/java/com/badmintoncommunity/module/reservation/service/ReservationConcurrencyTest.java#onlyOneWinnerForSameSlot`
> （修复前为红色，现已转绿并保留为回归守卫）

---

## 1. 修复前现象

同一球场、同一时段，被**4 个不同用户同时约上**。库里留下 4 条有效占用行。

```java
// ReservationConcurrencyTest.java
Map<String, Integer> outcome = Concurrency.race(4, i -> attempt(users.get(i), court1, start, end));
assertEquals(1, outcome.getOrDefault("OK", 0), ...);
```

实测结果：

```
Tests run: 47, Failures: 1
ReservationConcurrencyTest.onlyOneWinnerForSameSlot:66
  同一球场同一时段只允许 1 人约上，实际结果分布：{OK=4} ==> expected: <1> but was: <4>
```

**连续 5 轮全部 4 行** —— 不是偶发抖动，是稳定缺陷。

违反的规则（`requirements.md`）：

> 同一球场同一时间范围不能存在冲突的有效预约。

---

## 2. 修复前根因：REPEATABLE READ 下的一致性读快照被提前固定

`ReservationService.create` 的执行顺序（`@Transactional`，默认隔离级别 REPEATABLE READ）：

| 步骤 | 语句 | 读写类型 |
|---|---|---|
| ① | `TimeRules.validateInterval` / `ensureStartBookable` | 纯内存 |
| ② | `userMapper.findByIdForUpdate(userId)` | **当前读**（加锁） |
| ③ | `reservationMapper.countPersonalOverlap(...)` | **快照读** ← 快照在此建立 |
| ④ | `reservationMapper.listPersonalOnCourtAndDay(...)` | **快照读** |
| ⑤ | `courtMapper.findByIdForUpdate(courtId)` | **当前读**（加锁） |
| ⑥ | `reservationMapper.countCourtOverlap(...)` | **快照读** ← 用的仍是③建立的旧快照 |
| ⑦ | `reservationMapper.insert(...)` | 写 |

**关键**：InnoDB 在 REPEATABLE READ 下，事务的**一致性读快照在第一次普通 `SELECT` 时建立并固定**。
`SELECT ... FOR UPDATE` 是「当前读」，会去读最新已提交版本并加锁；
但步骤 ⑥ 的 `countCourtOverlap` 是普通 `SELECT COUNT(*)`，**走的是③固定下来的旧快照**。

于是并发时：

1. 4 个线程各自执行到③，**快照都被固定在"该时段还没有任何占用行"的那一刻**；
2. 4 个线程先后拿到 court 行锁（锁本身完全正常，实测每个都在等前一个提交，等待 19/28/33/41 ms）；
3. 但步骤 ⑥ 查的是旧快照 → **4 个线程全部读到 `占用 = 0`**；
4. 全部通过检查，全部 `insert`。

**锁没问题，是"锁住之后读到的还是旧数据"。**

---

## 3. 定位过程摘要（每一步都是受控对照）

| # | 实验 | 结果 | 结论 |
|---|---|---|---|
| 1 | 两事务用 `TransactionTemplate` 手工编排「锁 court → 查重 → 插入」 | 1 行 ✅，T1 等 T2 220ms | 锁、连接池、隔离级别本身都正常 |
| 2 | 同一场景换成生产 `ReservationService.create` | **4 行** ❌ | 差异出在 Service 内部 |
| 3 | 打印事务同步器 + 连接号 | `txActive=true`，连接全程一致 | 代理正常，不存在"子线程里事务失效" |
| 4 | 手工副本 + 逐线程打印**真实计数值** | 读到 1 / 1 / 1 / 0 → 1 行 ✅ | 副本正确，可作对照组 |
| 5 | **二分**：逐步加回 create 的语句 | 锁court→查重→插入 = **1**；加回前置查询 = **4** | 前置查询是把 1 变成 4 的那一步 |
| 6 | 在「查重→插入」之间人为加 0/5/50/200 ms 延迟 | 全都是 **1 行** | 否决「窗口太窄」假设 —— 锁确实保护了时间窗 |
| 7 | 4 线程打印 `CONNECTION_ID` | 422/423/424/425，前后一致无复用 | 否决「连接串号」假设 |
| 8 | 读者先普通 COUNT → 写者插入并提交 → 读者再 COUNT | 第一次 **0**，写者已提交，第二次仍 **0** | **命中根因：快照陈旧** |

实验 8 的原样输出：

```
读者 第一次普通 COUNT（建立快照）= 0
写者 已插入并即将提交
写者 已提交
读者 第二次普通 COUNT（同快照）= 0     ← 已提交的新行看不见
```

---

## 4. 修复前的防线为什么没拦住

| 防线 | 为什么失效 |
|---|---|
| `court` 行锁 | 保护的是 **court 表那一行**，不是"这个球场这个时段"。锁有效，但锁之后的读是旧快照 |
| `reservation` 索引 | `idx_reservation_court_time` 是**普通索引**，非唯一 → 数据库不会拒绝重复 |
| 后端 Service 校验 | 就是失效的那一环（第 ⑥ 步） |
| 前端校验 | 只影响 UI，改请求即可绕过 |

**修复前结论：当时这条业务规则在并发下没有一层真正兜底。**

---

## 5. 方案评估与本次选择

### 方案 A：把冲突检查改成「当前读」

**本次采用方案 A**：在 `ReservationMapper.countCourtOverlap` 末尾添加 `FOR UPDATE`，保留现有事务和先锁 court 行的顺序。

在 `countCourtOverlap` 的 SQL 末尾加 `FOR UPDATE`：

```sql
SELECT COUNT(*) FROM reservation
WHERE court_id = ? AND status = 1 AND start_time < ? AND end_time > ?
FOR UPDATE
```

- **利**：改动最小（一条 SQL）；`FOR UPDATE` 是当前读，强制看最新已提交数据并加间隙锁；语义上与现有"先锁 court 再查重"的设计一脉相承。
- **弊**：`FOR UPDATE` 加在 `COUNT(*)` 上，MySQL 会退化为「锁定匹配行再计数」，范围大时锁的量不小；间隙锁在 RR 下可能扩大锁范围，与其他事务的间隙锁互相阻塞的概率上升。**需实测**。

### 方案 B：数据库层加兜底约束

给 `reservation` 增加能表达"同场同时段不允许两条有效占用"的约束。

- **利**：真正的最后防线，即使 Service 有 bug 也拦得住；不依赖应用层正确性。
- **弊**：MySQL **不支持部分唯一索引 / 排除约束**（PostgreSQL 才有 `EXCLUDE`）。要落地必须改表结构，例如：
  - 加 `active_slot_key` 生成列（`status=1` 时等于"court_id+start_time+end_time"，否则 NULL），对它有唯一索引 —— 只能挡住**完全相同的时段**，挡不住**部分重叠**（10:00-11:00 vs 10:30-11:30）；
  - 或把粒度打散成"每个整点一行的 slot 表"，对 `(court_id, hour)` 建唯一索引 —— 能完整挡住重叠，但引入新表与新的一致性维护成本。
- **弊（共通）**：无法用唯一约束完整表达"区间不重叠"，这是关系模型的老问题。

### 方案 C：显式悲观锁（独立锁行 / 命名锁）

为每个 `(court_id, 日期)` 或 `(court_id, 时段)` 维护一条**锁行**，先 `SELECT ... FOR UPDATE` 锁它，再做检查。

- **利**：锁的语义清晰可控，不依赖隔离级别的边缘行为；范围可精确到"要保护什么"。
- **弊**：需要新表（或复用一张 `court_slot_lock`）；锁行本身要维护（谁创建、何时清理）；引入新的失败模式（锁行缺失时行为未定义）。

### 方案 D：优化锁 / 缩减事务边界

把 `create` 的 `@Transactional` 收窄到**只包住**「锁 court → 查重 → 插入」，把前置的纯校验挪到事务外。

- **利**：最贴近根因 —— 前置查询是"污染快照"的元凶，去掉它们快照就不会被提前固定；改动小、无新表。
- **弊**：治标 —— 只要将来有人在事务内再加一条普通 SELECT，缺陷就会复现。**没有兜底能力**。
- 可作为 A 或 C 的**配套优化**（减小锁持有时间），不宜单独使用。

### 方案 E：业务层串行化（单线程队列 / 分布式锁）

- **弊**：违反项目约束 —— `project-core.md` 明确"不得为了技术先进擅自引入 MQ、微服务、分布式组件"。且 MVP 只有一个场馆，收益不成比例。
- 除非将来多实例部署，否则不建议。

---

## 6. 本次修复与验证

- 用户亲手修改 `countCourtOverlap` 的 SQL，单独运行 `onlyOneWinnerForSameSlot`：**1 个用例通过，0 失败**。
- 随后运行预约相关四个测试类：**45 个用例通过，0 失败、0 错误**，包括真实 MySQL 并发测试；整理注释和 SQL 格式后再次运行完整测试：**48 个用例通过，0 失败、0 错误**。
- 测试启动阶段曾有 `ActivityStatusScheduler` 早于测试表初始化运行的后台日志错误；用例未因此失败，此工程问题另行处理。
- `ActivityService.create` 也调用同一个冲突查询。后续已增加 `ActivityReservationConcurrencyTest`，覆盖单球场与个人预约并发争抢；2026-10-27 又增加双球场活动与个人预约争抢，以及第二块场冲突时不得留下活动资源的用例。
- 目前只验证了已有测试覆盖的并发与业务规则，不宣称所有部署形态和竞争时序都已经测尽。

---

## 7. 影响面

`RegistrationService.register` 也采用“锁活动行 → 查报名 → 计数 → 写入”的模式，但它的快照建立时机与预约修复前不同：

```java
Activity activity = activityMapper.findByIdForUpdate(activityId);  // 当前读
// ... 状态校验 ...
Registration existing = registrationMapper.findByUserAndActivity(userId, activityId); // 锁后首次普通读
long active = registrationMapper.countActive(activityId);          // 沿用上述快照
if (active >= activity.getMaxMembers()) { throw ... }
```

当前代码在取得 `activity` 行锁后才做第一次普通读，后到事务应能看到先到事务提交的报名行；它没有预约修复前“先建立旧快照、再等锁”的相同执行顺序。报名侧已增加 `RegistrationConcurrencyTest`，覆盖 4 人争最后 1 个名额、取消后重新报名与新报名竞争、取消与新报名交错、同一成员取消与重报时另一成员争名额，以及拒绝后重试；这些用例验证了当前实现，但不能证明未来增加锁前普通读或其他事务交错仍安全。三方竞争用例只断言不超员、报名行不重复和结果码合法；同时起跑并不能保证所有先后顺序都执行到。

> 注：`registration` 表有 `UNIQUE KEY uk_registration_user_activity (user_id, activity_id)`，
> 能挡住"同一用户重复报名"，但挡不住"名额超卖"（不同用户各报一行）。

---

## 8. 2026-10-27：双球场交错暴露范围锁死锁

新增双球场活动与个人预约竞争第二块场的测试后，完整测试出现一次 `{RESERVATION_OK=1, UNEXPECTED:DeadlockLoserDataAccessException=1}`。MySQL `SHOW ENGINE INNODB STATUS` 显示：活动事务持有 `idx_reservation_court_time` 上的空范围锁，等待第二块 `court` 行；个人预约持有该 `court` 行，插入 `reservation` 时等待上述索引间隙，形成循环等待。数据库回滚活动事务，未出现重复占场，但调用方收到数据库异常，而非业务冲突码。

仅将个人预约和活动创建两个写入占场的事务设为 `READ COMMITTED`；保留球场行锁和 `countCourtOverlap` 的 `FOR UPDATE`。该隔离级别下，普通查询每次读取最新已提交版本，锁定读通常不取得搜索间隙锁（外键与唯一性检查除外）。同一球场仍由 `court` 行锁串行化。修改后，双球场测试、已有并发测试以及完整后端测试 67 个用例通过。测试证明了覆盖场景的结果，不代表所有数据库死锁已消除。
