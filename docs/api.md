# API 设计

> 本文档为 API 初稿，业务规则来源于 `requirements.md`、`database.md`、`decisions.md`、`business-flows.md`。
>
> 本初稿只定义**接口契约**：路径、参数、权限、资源归属、响应、错误语义。不写 Java / Controller / Service / SQL。
>
> 认证机制（Session 或 JWT）**待 ADR 决策**，本设计不绑定具体实现。
> 统一响应格式与 HTTP 状态码策略为 MVP 推荐方案，标注 **待最终技术决策确认**。

## 1. 全局约定

### 1.1 基础

- 统一前缀：`/api`。
- JSON 字段命名统一 **snake_case**（与数据库字段、设计文档一致）。
- 时间格式：本地时间 `yyyy-MM-dd'T'HH:mm:ss`（例 `2026-09-08T19:00:00`）。
- 日期格式：`yyyy-MM-dd`。
- 预约/活动时间**整点对齐**（分钟、秒必须为 0）——Service 校验，接口层只做格式校验。
- 时间与窗口规则（7 天窗口、4 小时取消、不跨天）**不是前端规则**，一律由 Service 执行，接口不接收前端判定结果。

### 1.2 认证

- 除标注「匿名」的接口外，均需登录。
- 登录凭据的携带方式（Header / Cookie）待认证 ADR 确认；本文档以「请求需携带登录身份」描述，不绑定实现。

### 1.3 分页（统一）

- Query 参数：`page`（从 1 开始，默认 1）、`page_size`（默认 20，最大 50）。
- 列表响应统一结构（放在 `data` 内）：

```text
data = {
  "list": [ ... ],
  "total": 0,       # 满足条件的总条数
  "page": 1,
  "page_size": 20
}
```

- 不使用游标分页、不每个接口发明新分页格式。

### 1.4 枚举值（与 database.md / business-flows.md 一致，接口上下发数字）

| 枚举 | 值 |
|---|---|
| user.role | 0=普通用户，1=管理员 |
| court.status | 1=可预约，2=不可预约 |
| activity.status | 0=待审核，1=已发布，2=已驳回，3=已取消，4=已结束 |
| reservation.status | 1=有效，2=已取消 |
| registration.status | 1=已报名，2=已取消 |

> 活动「是否满员」不落状态：由响应中的 `joined_count < max_members` 推导，接口不下发 full 状态字段。

### 1.5 可见性规则（读取接口通用）

- 活动详情/成员列表等按 activity.status 区分可见性：
  - `1 已发布` / `4 已结束`：公开可读；
  - `0 待审核` / `2 已驳回` / `3 已取消`：仅创建者与管理员可见。
- 场地占用查询返回的是**占用事实**（时间、来源类型），**不是**「是否可约」的结论——可用性判断只存在于 Service 的创建/报名流程中。

---

## 2. 认证与用户 API

### 2.1 用户注册

**POST /api/auth/register**

**用途**：创建普通用户账号。

**权限**：匿名。

**资源归属**：创建自己的账号，不涉及他人资源。

**Request（Body）**

| 参数 | 必填 | 含义与约束 |
|---|---|---|
| username | 是 | 登录名，唯一；长度 ≤64 |
| password | 是 | 明文密码，仅用于哈希后入库；长度 ≥6 |
| nickname | 是 | 展示昵称；长度 ≤64 |

**Response**：`code=0`，`data=null`。注册成功后需调用登录接口获取凭据。

**Success**：插入一行 `role=0` 的用户，密码以哈希形式保存。

**Failure**

- `USERNAME_TAKEN`：用户名已存在
- `PARAM_INVALID`：参数缺失或格式不合法

### 2.2 用户登录

**POST /api/auth/login**

**用途**：校验凭据并签发登录态。

**权限**：匿名。

**Request（Body）**

| 参数 | 必填 | 含义与约束 |
|---|---|---|
| username | 是 | 登录名 |
| password | 是 | 明文密码 |

**Response**：`data.user = {id, username, nickname, avatar_url, role}`；登录凭据字段（token / session）**待认证 ADR 确认**后补充。

**Success**：校验通过，返回用户信息与登录态。

**Failure**

- `INVALID_CREDENTIALS`：用户名或密码错误（统一提示，不区分账号是否存在）

### 2.3 获取个人信息

**GET /api/users/me**

**用途**：获取当前登录用户资料。

**权限**：普通用户、管理员。

**资源归属**：本人。

**Response**：`data = {id, username, nickname, avatar_url, role, create_time}`。

**Failure**：`UNAUTHORIZED`（未登录或凭据失效）。

### 2.4 修改个人信息

**PUT /api/users/me**

**用途**：修改本人可编辑资料。

**权限**：普通用户、管理员。

**资源归属**：只能改自己；管理员也不能通过本接口改他人（他人资料不在 MVP 范围）。

**Request（Body）**

| 参数 | 必填 | 含义与约束 |
|---|---|---|
| nickname | 二选一 | 展示昵称，≤64 |
| avatar_url | 二选一 | 头像 URL |

> 至少传一项。`username`、`role` 不可修改（username 为登录键，不提供改名）。

**Response**：`data = {id, username, nickname, avatar_url, role}`。

**Failure**：`PARAM_INVALID`、`UNAUTHORIZED`。

### 2.5 退出登录

**POST /api/auth/logout**

**用途**：结束当前登录态。

**权限**：普通用户、管理员。

**说明**：采用 Session 时由服务端失效会话；采用 JWT 时由客户端丢弃凭据（MVP 无服务端黑名单）。实现细节待认证 ADR。

**Response**：`code=0`，`data=null`。幂等：未登录调用也返回成功。

---

## 3. 场馆与球场 API

### 3.1 查询场馆

**GET /api/venues**

**用途**：获取场馆列表（V1 只有一行）。

**权限**：匿名。

**Response**：`data.list = [{id, name, address}]`（数据量固定，不分页）。

**Failure**：无业务失败。

### 3.2 查询球场列表

**GET /api/venues/{venueId}/courts**

**用途**：获取某场馆下的球场（如「1 号场」）。

**权限**：匿名。

**Request（Query）**

| 参数 | 必填 | 含义与约束 |
|---|---|---|
| status | 否 | 按可约状态过滤：1=可预约，2=不可预约；缺省返回全部 |

**Response**：`data.list = [{id, venue_id, name, status}]`。

**Failure**：`VENUE_NOT_FOUND`（场馆不存在）。

### 3.3 查询球场某时间范围的预约情况

**GET /api/courts/{courtId}/occupancy?date=2026-09-08**

**用途**：给前端提供某天该球场的**占用事实**（已占用时段及其来源），用于渲染时间轴/灰色时段。是否可约的最终判定仍在创建预约的 Service 中。

**权限**：匿名。

**Request（Query）**

| 参数 | 必填 | 含义与约束 |
|---|---|---|
| date | 是 | 查询日期（单日），`yyyy-MM-dd` |

**Response**

```text
data = {
  "court_id": 1,
  "date": "2026-09-08",
  "occupied": [
    {"start_time": "2026-09-08T19:00:00", "end_time": "2026-09-08T21:00:00",
     "kind": "PERSONAL" | "ACTIVITY", "activity_id": null}
  ]
}
```

- `kind` 由归属推导（不存字段）：`user_id` 非空 → PERSONAL；`activity_id` 非空 → ACTIVITY。
- 只返回 `status=1`（有效）且与查询日相交的占用。

**Failure**：`COURT_NOT_FOUND`、`PARAM_INVALID`（date 格式错）。

### 3.4 管理员新增球场

**POST /api/admin/courts**

**用途**：在场馆下新增球场，默认「可预约」。

**权限**：管理员。

**资源归属**：管理端资源操作，作用于任意场馆/球场。

**Request（Body）**

| 参数 | 必填 | 含义与约束 |
|---|---|---|
| venue_id | 是 | 所属场馆 |
| name | 是 | 球场名，同场馆内唯一，≤32 |

**Response**：`data = {id, venue_id, name, status: 1}`。

**Failure**：`COURT_NAME_DUPLICATED`（同场馆重名）、`VENUE_NOT_FOUND`、`FORBIDDEN`。

### 3.5 管理员修改球场

**PUT /api/admin/courts/{courtId}**

**用途**：修改球场名。

**权限**：管理员。

**Request（Body）**

| 参数 | 必填 | 含义与约束 |
|---|---|---|
| name | 是 | 新名称，同场馆内唯一 |

**Response**：`data = {id, venue_id, name, status}`。

**Failure**：`COURT_NOT_FOUND`、`COURT_NAME_DUPLICATED`、`FORBIDDEN`。

### 3.6 管理员停用球场

**PUT /api/admin/courts/{courtId}/disable**

**用途**：将球场置为不可预约（如维护中）。存在**未来有效占用**（个人与活动来源都算）时拒绝停用。

**权限**：管理员。

**Response**：`data = {id, venue_id, name, status: 2}`。

**Failure**：`COURT_NOT_FOUND`、`ACTIVE_RESERVATION_EXISTS`（存在未来有效预约，禁止停用）、`FORBIDDEN`。

### 3.7 管理员恢复球场

**PUT /api/admin/courts/{courtId}/enable`

**用途**：恢复为可预约。

**权限**：管理员。

**Response**：`data = {id, venue_id, name, status: 1}`。

**Failure**：`COURT_NOT_FOUND`、`FORBIDDEN`。

---

## 4. 个人预约 API

### 4.1 创建个人球场预约

**POST /api/reservations**

**用途**：预约一块球场的连续整点时段（如 19:00–21:00）。

**权限**：普通用户、管理员（管理员也以普通身份个人预约）。

**资源归属**：创建的是自己的预约行。

**Request（Body）**

| 参数 | 必填 | 含义与约束 |
|---|---|---|
| court_id | 是 | 目标球场 |
| start_time | 是 | 开始时间（整点） |
| end_time | 是 | 结束时间（整点，> start_time，不跨天） |

**接口级承诺（对应 business-flows 动作 3）**

- 7 天窗口、整点、不跨天、球场可用性、个人重叠 ≤2、球场冲突全部由 **Service** 校验；接口只负责透传时间并做基础格式校验。
- 「同一用户重叠时段最多 2 个有效个人预约」**不交给前端计算**——前端不做任何限制性判定，只展示接口结果。
- 并发下两个请求抢同一时段，后端**必然只有一个成功**，另一个返回 `COURT_ALREADY_RESERVED`。

**Response**：`data = {id, court_id, start_time, end_time, status: 1}`（`activity_id=null`，为个人行）。

**Success**：插入一条有效个人占用行；该场该时段对所有来源关闭。

**Failure**

- `COURT_NOT_FOUND`、`COURT_UNAVAILABLE`（球场不可预约）
- `INVALID_TIME`（非整点 / end ≤ start / 跨天）
- `BOOKING_WINDOW_EXCEEDED`（超出未来 7 个自然日窗口，含「最早约明天」边界）
- `PERSONAL_RESERVATION_LIMIT_EXCEEDED`（与本人已有有效个人预约时间重叠数已达 2）
- `COURT_ALREADY_RESERVED`（该场该时段已有有效占用——个人或活动来源都算）

### 4.2 查询我的预约

**GET /api/reservations/mine**

**用途**：查看本人个人预约（`activity_id IS NULL` 的行；**不含**活动占场行）。

**权限**：普通用户、管理员。

**资源归属**：只返回自己的行。

**Request（Query）**：`page`、`page_size`；`status`（1=有效，2=已取消，缺省=有效）；`start_from`（可选，预约开始时间下限）。

**Response**：列表元素 `{id, court_id, court_name, start_time, end_time, status, cancel_time, create_time}`，按 start_time 升序。

**Failure**：`UNAUTHORIZED`。

### 4.3 查询预约详情

**GET /api/reservations/{id}`

**用途**：查看单条预约明细。

**权限**：普通用户、管理员。

**资源归属**：本人可查；管理员可查任意个人预约（仅个人行有归属，活动占场行不经此接口暴露）。

**Response**：`data = {id, court_id, court_name, user_id, activity_id, start_time, end_time, status, cancel_time, create_time}`。

**Failure**：`RESERVATION_NOT_FOUND`、`FORBIDDEN`（非本人且非管理员）。

### 4.4 取消个人预约

**POST /api/reservations/{id}/cancel`

**用途**：取消自己的个人预约（非删除，状态置为已取消）。

**权限**：普通用户、管理员（以个人身份取消自己的预约）。

**资源归属**：只能取消 `user_id=自己` 的个人行（`activity_id IS NULL`）。

**Request**：无 Body。

**接口级承诺**

- 剩余时间 ≥4 小时才允许：`now ≤ start_time - 4h`，由 Service 判定（规则 9/10）。
- 幂等：重复取消返回成功（`status` 已为 2 视为已取消）。

**Response**：`data = {id, status: 2, cancel_time}`。

**Failure**

- `RESERVATION_NOT_FOUND`（不存在或不是本人）
- `CANCEL_WINDOW_EXCEEDED`（开场前不足 4 小时，普通用户不可自行取消）

---

## 5. 活动 API

### 5.1 创建活动

**POST /api/activities**

**用途**：创建羽毛球活动。**本接口一个事务内完成三件事**：创建活动（待审核）、锁定所选球场、把创建者记为第一名报名者。

**权限**：普通用户、管理员（所有登录用户都可创建）。

**资源归属**：创建者成为活动的 `creator_id`，拥有取消/查看未发布详情等本人权益。

**Request（Body）**

| 参数 | 必填 | 含义与约束 |
|---|---|---|
| title | 是 | 活动标题，≤100 |
| description | 否 | 活动描述，≤2000 |
| start_time | 是 | 开始时间（整点） |
| end_time | 是 | 结束时间（整点，> start_time，不跨天） |
| max_members | 是 | 报名人数上限，≥1（创建者自动占 1 个名额） |
| court_ids | 是 | 球场 id 数组，至少 1 个；请求按此锁场，**顺序无关，Service 按 court_id 升序加锁** |

**接口级承诺（对应 business-flows 动作 5）**

- 创建成功即 `status=0（待审核）`；**无草稿态，创建即提交审核**。
- 创建同时为每个 `court_id` 写入一条活动占场 reservation（`user_id=null`），并插入创建者的 registration——**整个动作是一个业务事务**，任一块场冲突则整体失败、整体回滚。
- 7 天窗口对活动锁场**同等生效**（requirements 规则 17）。
- 待审核期间球场即被占用——这是既定业务，接口文档明示，避免前端误解。

**Response**：`data = {id, creator_id, title, status: 0, start_time, end_time, max_members, court_ids: [..], joined_count: 1}`。

**Success**：新增一条待审核活动 + N 条有效活动占场 + 创建者报名行，全部一致落库。

**Failure**

- `PARAM_INVALID`（title/时间/人数/球场列表不合法）
- `INVALID_TIME`、`BOOKING_WINDOW_EXCEEDED`
- `COURT_NOT_FOUND`、`COURT_UNAVAILABLE`
- `COURT_ALREADY_RESERVED`（任一块场冲突即整体失败，错误中携带首个冲突球场）

### 5.2 查询活动列表

**GET /api/activities**

**用途**：浏览公开活动（已发布 + 已结束）。

**权限**：匿名。

**Request（Query）**

| 参数 | 必填 | 含义与约束 |
|---|---|---|
| status | 否 | 1=已发布，4=已结束；缺省返回两者（时间倒序）。**非公开状态（0/2/3）对非管理员不可查询** |
| start_from | 否 | 活动开始时间下限（如只列出未来活动） |
| start_to | 否 | 活动开始时间上限 |
| page / page_size | 否 | 分页 |

**Response**：列表元素 `{id, title, creator_id, creator_nickname, start_time, end_time, max_members, joined_count, status}`。`joined_count` 为 `status=1` 的报名行数，实时计算。

**Failure**：`FORBIDDEN`（普通用户请求了非公开 status）。

### 5.3 查询活动详情

**GET /api/activities/{id}`

**用途**：活动详情。

**权限**：匿名（可见性受限）。

**资源归属/可见性**：`status∈{1,4}` 公开；`status∈{0,2,3}` 仅创建者与管理员。

**Response**：`data = {id, creator_id, creator_nickname, title, description, start_time, end_time, max_members, joined_count, status, reviewer_id, review_time, reject_reason, create_time, court_ids}`。

**Failure**：`ACTIVITY_NOT_FOUND`（不存在或当前用户无权查看）。

### 5.4 查询我创建的活动

**GET /api/activities/mine`

**用途**：查看自己创建的活动（含全部状态）。

**权限**：普通用户、管理员。

**资源归属**：`creator_id=自己`。

**Request（Query）**：`page`、`page_size`、`status`（可选，缺省全部状态）。

**Response**：列表元素同 5.2（按 start_time 倒序）。

### 5.5 创建者取消活动

**POST /api/activities/{id}/cancel`

**用途**：创建者取消自己的活动（待审核或已发布、且未开始）。

**权限**：该活动创建者。

**资源归属**：只能取消 `creator_id=自己` 的活动。

**接口级承诺（对应 business-flows 动作 9）**

- 一个事务内：活动 `status → 3`；其名下全部有效占场 reservation → `2 已取消`（场地释放）；全部 registration → `2 已取消`（含创建者自己）。
- 仅限活动开始前；已驳回/已结束/已取消不可再操作。

**Response**：`data = {id, status: 3}`。

**Failure**：`ACTIVITY_NOT_FOUND`、`FORBIDDEN`（非创建者）、`ACTIVITY_STATE_INVALID`（状态不允许或已开始）。

### 5.6 管理员查看待审核活动

**GET /api/admin/activities/pending`

**用途**：审核队列。

**权限**：管理员。

**Request（Query）**：`page`、`page_size`。

**Response**：列表元素同 5.2（`status=0`，含创建者昵称，按创建时间升序——先创建先审）。

### 5.7 管理员通过活动

**POST /api/admin/activities/{id}/approve`

**用途**：审核通过，活动进入可报名状态。

**权限**：管理员。

**接口级承诺（对应 business-flows 动作 6）**

- 写回 `reviewer_id=当前管理员`、`review_time=now`、`status → 1`。
- 活动占场与创建者报名**保持不变**（场地继续被占用，报名名额继续生效）。

**Response**：`data = {id, status: 1, reviewer_id, review_time}`。

**Failure**：`ACTIVITY_NOT_FOUND`、`ACTIVITY_STATE_INVALID`（非待审核，已被处理）、`FORBIDDEN`。

### 5.8 管理员驳回活动

**POST /api/admin/activities/{id}/reject`

**用途**：审核驳回，并释放该活动占用的场地、作废报名。

**权限**：管理员。

**Request（Body）**

| 参数 | 必填 | 含义与约束 |
|---|---|---|
| reject_reason | 是 | 驳回原因，≤255，将展示给创建者 |

**接口级承诺（对应 business-flows 动作 6 驳回分支）**

- 一个事务内：活动 `status → 2` + 审核痕迹；名下有效占场 → `2 已取消`；名下 registration → `2 已取消`。

**Response**：`data = {id, status: 2, reviewer_id, review_time, reject_reason}`。

**Failure**：`ACTIVITY_NOT_FOUND`、`ACTIVITY_STATE_INVALID`、`FORBIDDEN`。

### 5.9 管理员取消活动

**POST /api/admin/activities/{id}/cancel`

**用途**：管理员取消活动（可含已开始未结束的特殊情况）。

**权限**：管理员。

**接口级承诺**：同 5.5 的事务行为（状态 →3 + 释放占场 + 作废报名），但不受「未开始」限制。

**Response**：`data = {id, status: 3}`。

**Failure**：`ACTIVITY_NOT_FOUND`、`ACTIVITY_STATE_INVALID`（已驳回/已结束/已取消）、`FORBIDDEN`。

---

## 6. 活动报名 API

### 6.1 报名活动

**POST /api/activities/{activityId}/registration`

**用途**：当前用户报名活动（幂等；取消过的用户可重新报名，不产生第二行记录）。

**权限**：普通用户、管理员。

**资源归属**：作用于自己的报名记录。

**接口级承诺（对应 business-flows 动作 7）**

- 只有 `status=1（已发布）` 且当前时间在活动开始之前可报名。
- 创建者已在创建时占用一个名额（不再重复计）。
- 满员与并发防超卖由 **Service 以 activity 行锁保证**；并发抢最后一个名额时只有一个成功，其余返回 `ACTIVITY_FULL`。
- 同一用户仅一条 registration：取消过 → 将 `status` 从 2 置回 1；从未报名 → 新建。**不新增第二行**（唯一约束 `(user_id, activity_id)` 兜底）。
- 已报名有效时重复调用 → 幂等成功（返回当前记录，不重复占名额）。

**Response**：`data = {id, user_id, activity_id, status: 1}`。

**Failure**

- `ACTIVITY_NOT_FOUND`、`ACTIVITY_STATE_INVALID`（非已发布）
- `REGISTRATION_WINDOW_CLOSED`（已开始或已结束）
- `ACTIVITY_FULL`（已达 max_members，并发下最后一个名额可能刚被抢走）

### 6.2 取消报名

**DELETE /api/activities/{activityId}/registration`

**用途**：当前用户取消自己的报名（状态置为已取消，名额释放）。

**权限**：普通用户、管理员。

**资源归属**：只作用于自己的报名行。

**接口级承诺（对应 business-flows 动作 8）**

- 活动尚未结束可取消；活动已取消/已驳回时，报名已由系统级联置为已取消，本接口无需也不允许操作。
- 重复取消（已是已取消态）返回 `NOT_REGISTERED`。

**Response**：`data = {id, status: 2}`。

**Failure**：`ACTIVITY_NOT_FOUND`、`ACTIVITY_STATE_INVALID`（已结束/已取消/已驳回）、`NOT_REGISTERED`（未报名或已取消）。

### 6.3 查询活动报名成员

**GET /api/activities/{activityId}/registrations`

**用途**：查看某活动已报名成员列表。

**权限**：登录用户。

**可见性**：活动 `status∈{1,4}` 公开可看；`status∈{0,2,3}` 仅创建者与管理员可看。

**Request（Query）**：`page`、`page_size`。

**Response**：`data.list = [{id, user_id, nickname, avatar_url, create_time}]`（仅 `status=1` 已报名成员）。

**Failure**：`ACTIVITY_NOT_FOUND`、`FORBIDDEN`（无可见权限）。

### 6.4 查询我参加的活动

**GET /api/registrations/mine`

**用途**：查看我的报名记录及其活动。

**权限**：普通用户、管理员。

**资源归属**：`user_id=自己`。

**Request（Query）**：`page`、`page_size`、`status`（1=已报名，2=已取消，缺省=已报名）。

**Response**：列表元素 `{registration: {id, status, create_time}, activity: {id, title, start_time, end_time, max_members, joined_count, status, creator_nickname}}`。

---

## 7. 社区 API

### 7.1 帖子列表

**GET /api/posts`

**用途**：按时间倒序浏览帖子。

**权限**：匿名。

**Request（Query）**：`page`、`page_size`。

**Response**：列表元素 `{id, user_id, author_nickname, author_avatar_url, title, content, like_count, comment_count, create_time}`。`like_count`/`comment_count` 实时统计。

### 7.2 帖子详情

**GET /api/posts/{id}`

**用途**：单帖详情。

**权限**：匿名（`liked` 字段需登录才有意义）。

**Response**：`data = {id, user_id, author_nickname, author_avatar_url, title, content, like_count, comment_count, liked, create_time}`。`liked` = 当前登录用户是否已点赞（未登录为 false）。

**Failure**：`POST_NOT_FOUND`（含被删除）。

### 7.3 创建帖子

**POST /api/posts`

**权限**：普通用户、管理员。

**Request（Body）**

| 参数 | 必填 | 约束 |
|---|---|---|
| title | 是 | ≤200 |
| content | 是 | 非空 |

**Response**：`data = {id, user_id, title, content, create_time}`。

### 7.4 删除自己的帖子

**DELETE /api/posts/{id}`

**权限**：普通用户、管理员。

**资源归属**：只能删 `user_id=自己` 的帖子。删除即物理删除，评论与点赞级联清理（database.md 外键策略）。

**Response**：`data=null`。幂等：重复删除返回 `POST_NOT_FOUND`。

**Failure**：`POST_NOT_FOUND`、`FORBIDDEN`（非作者）。

### 7.5 管理员删除帖子

**DELETE /api/admin/posts/{id}`

**用途**：处理违规内容（requirements：管理员处理违规内容 = 删除）。

**权限**：管理员。

**资源归属**：可删任意帖子，级联清理评论与点赞。

**Response**：`data=null`。

**Failure**：`POST_NOT_FOUND`、`FORBIDDEN`。

### 7.6 评论帖子

**POST /api/posts/{id}/comments`

**权限**：普通用户、管理员。

**Request（Body）**：`content`（必填，≤1000）。

**Response**：`data = {id, post_id, user_id, content, create_time}`。

**Failure**：`POST_NOT_FOUND`。

### 7.7 查询帖子评论

**GET /api/posts/{id}/comments`

**权限**：匿名。

**Request（Query）**：`page`、`page_size`。

**Response**：`data.list = [{id, user_id, nickname, avatar_url, content, create_time}]`，按 create_time 升序。

**Failure**：`POST_NOT_FOUND`。

### 7.8 点赞帖子

**POST /api/posts/{id}/like`

**权限**：普通用户、管理员。

**接口级承诺（对应 business-flows 动作 15）**

- 幂等：已点赞再次点赞返回成功，不产生第二行（复合主键 `(post_id, user_id)` 兜底）。
- 无计数列，点赞数 = post_like 行数。

**Response**：`data = {liked: true}`。

**Failure**：`POST_NOT_FOUND`。

### 7.9 取消点赞

**DELETE /api/posts/{id}/like`

**权限**：普通用户、管理员。

**接口级承诺**：幂等——未点赞时取消也返回成功。

**Response**：`data = {liked: false}`。

**Failure**：`POST_NOT_FOUND`。

---

## 8. 管理员 API

管理员 = `user.role=1`。管理端接口统一在 `/api/admin/*` 下；失败语义与普通接口一致，返回 `FORBIDDEN` 表示非管理员。

| 接口 | 方法 + Path | 说明 | 明细 |
|---|---|---|---|
| 新增球场 | POST /api/admin/courts | 默认可预约 | 3.4 |
| 修改球场 | PUT /api/admin/courts/{id} | 改名 | 3.5 |
| 停用球场 | PUT /api/admin/courts/{id}/disable | 有未来有效占用则拒绝 | 3.6 |
| 恢复球场 | PUT /api/admin/courts/{id}/enable | | 3.7 |
| 待审核队列 | GET /api/admin/activities/pending | | 5.6 |
| 审核通过 | POST /api/admin/activities/{id}/approve | | 5.7 |
| 审核驳回 | POST /api/admin/activities/{id}/reject | 释放占场、作废报名 | 5.8 |
| 取消活动 | POST /api/admin/activities/{id}/cancel | 同上，且不受未开始限制 | 5.9 |
| 删除帖子 | DELETE /api/admin/posts/{id} | 违规内容处理 | 7.5 |

**管理员特殊取消个人预约**（对应 requirements 规则 11；不在个人取消 4 小时窗口限制内）：

**POST /api/admin/reservations/{id}/cancel`

- 用途：特殊情况下由管理员取消**任意个人预约**（`activity_id IS NULL`），不受 4 小时窗口限制。
- 权限：管理员。
- 事务语义：单行状态更新（`status → 2`、`cancel_time=now`）。
- Response：`data = {id, status: 2, cancel_time}`。
- Failure：`RESERVATION_NOT_FOUND`（不存在或非个人行，如活动占场行不经此接口）、`FORBIDDEN`。

---

## 9. 统一响应

**待最终技术决策确认**（推荐 MVP 方案，理由见第 10 节）。

### 9.1 成功

```text
HTTP 200
{ "code": 0, "message": "success", "data": { ... } }
```

### 9.2 失败

```text
{ "code": "COURT_ALREADY_RESERVED", "message": "该球场该时段已被预约", "data": null }
```

- 成功 `code=0`；失败 `code` 为大写下划线业务码（见第 10 节），`message` 为可直接展示的文案。
- 列表数据统一用 `{list, total, page, page_size}`（见 1.3）。

---

## 10. HTTP 状态码与错误码策略

**待最终技术决策确认。** MVP 推荐：**HTTP 状态码只表达传输/认证/授权/格式层面；业务规则结果一律 HTTP 200 + 业务码**，客户端只解析一个通道。

| HTTP | 含义 | 说明 |
|---|---|---|
| 200 | 请求已处理 | 业务成功（code=0）或业务失败（code≠0）都在此返回 |
| 400 | 参数/请求格式错误 | 缺参、类型错、枚举值非法等，body 带 `code=PARAM_INVALID` |
| 401 | 未认证 | 未登录或凭据失效 |
| 403 | 无权限 | 非管理员 / 非本人资源（业务码 `FORBIDDEN`） |
| 404 | 路径不存在 | 请求的 URL 不存在（**不用于**「记录不存在」，后者走 200+业务码，如 `ACTIVITY_NOT_FOUND`） |
| 500 | 未预期异常 | 不向前端暴露内部细节 |

**不使用 201/409 的原因**：创建类成功统一 200 + code=0，避免前端「201 vs 200」双分支；409 与 200+业务码语义重叠，MVP 阶段不引入两套错误表达。若后续接入网关/监控需要语义化状态码，可另行映射（属于实现选择，不影响 code 定义）。

### 错误码总表

| code | HTTP | 含义 |
|---|---|---|
| USERNAME_TAKEN | 200 | 用户名已存在 |
| INVALID_CREDENTIALS | 200 | 用户名或密码错误 |
| INVALID_TIME | 200 | 时间不合法（非整点 / end ≤ start / 跨天） |
| BOOKING_WINDOW_EXCEEDED | 200 | 超出未来 7 个自然日预约窗口 |
| COURT_NOT_FOUND | 200 | 球场不存在 |
| COURT_UNAVAILABLE | 200 | 球场不可预约（已停用） |
| COURT_ALREADY_RESERVED | 200 | 该场该时段已被有效占用（个人或活动来源） |
| PERSONAL_RESERVATION_LIMIT_EXCEEDED | 200 | 本人重叠时段的有效个人预约已达 2 |
| RESERVATION_NOT_FOUND | 200 | 预约不存在或非本人 |
| CANCEL_WINDOW_EXCEEDED | 200 | 开场前不足 4 小时，普通用户不可自行取消 |
| ACTIVE_RESERVATION_EXISTS | 200 | 球场存在未来有效预约，禁止停用 |
| COURT_NAME_DUPLICATED | 200 | 同场馆球场重名 |
| VENUE_NOT_FOUND | 200 | 场馆不存在 |
| ACTIVITY_NOT_FOUND | 200 | 活动不存在或当前用户无权查看 |
| ACTIVITY_STATE_INVALID | 200 | 活动状态不允许该操作 |
| ACTIVITY_FULL | 200 | 活动已满员 |
| REGISTRATION_WINDOW_CLOSED | 200 | 报名窗口已关闭（已开始或已结束） |
| NOT_REGISTERED | 200 | 未报名该活动或已取消 |
| POST_NOT_FOUND | 200 | 帖子不存在（含已删除） |
| PARAM_INVALID | 400 | 参数缺失/格式/枚举不合法 |
| UNAUTHORIZED | 401 | 未认证或凭据失效 |
| FORBIDDEN | 403 | 已认证但无权限（角色或资源归属不符） |

> 说明：`ALREADY_REGISTERED`（重复报名）不作为错误——报名接口对已报名用户幂等返回成功（business-flows 动作 7）；并发重复插入由唯一约束拦截并同样转为幂等成功。

---

## 11. 分页规范

- 查询参数：`page`（≥1，默认 1）、`page_size`（默认 20，最大 50）。
- 响应：`data = {list, total, page, page_size}`。
- 应用范围：活动列表、我创建的活动、我的预约、我的报名、活动成员、帖子列表、评论列表、待审核活动。
- 场馆/球场等固定少量数据不分页。

---

## 12. API 层与 Service 层职责边界

> API 文档描述**接口契约**；Service 内部实现细节（锁、事务、窗口判定）**不暴露为 API 参数**，也不要求前端配合判定。

### API 层负责

- 参数接收与基础格式校验（必填、类型、长度、时间格式、枚举值域）。
- 身份信息获取（从登录态解析当前用户）。
- 将业务失败映射为统一响应（业务码 + 文案）。
- 权限入口判定（角色/资源归属）可作为粗筛，但**最终归属校验仍以 Service 为准**。

### Service 层负责（对应 database.md 第 7 节 / business-flows）

- 时间窗口：整点对齐、end>start、不跨天、7 天窗口、4 小时取消窗口。
- 球场可用性与统一冲突检查（个人预约与活动占场同一套规则、同一把球场锁）。
- 个人重叠预约数量限制（≤2，只统计个人行）。
- 活动状态机与跨表级联：创建锁场、驳回/取消释放占场与作废报名、结束流转。
- 满员判定与并发防超卖（activity 行锁）。
- 资源归属与「恰一归属」不变量（个人行/活动行）。
- 事务边界与并发控制（悲观行锁、加锁顺序）。
- 审核人必须为管理员等角色校验。

> 典型示例：创建预约接口只收 `court_id / start_time / end_time`——「7 天窗口」「重叠 ≤2」「冲突」全部由 Service 判定并以业务码返回，前端不做任何限制性计算。
