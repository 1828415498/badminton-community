# 数据库设计

## 1. 状态与范围

**MVP 定稿。**

- 共 9 张表：`user`、`venue`、`court`、`activity`、`reservation`、`registration`、`post`、`comment`、`post_like`，不增不减。
- 本文只描述**逻辑设计**（字段、约束、职责边界），不包含建表 SQL，不包含 Java 代码。
- 业务规则以 [需求分析](requirements.md) 为准，本文不重复定义业务；只记录**影响建模**的部分。
- 约定：主键统一 `BIGINT` 自增；时间统一 `DATETIME`；命名统一 snake_case；全表不做物理删除、不加逻辑删除字段。

## 2. 设计前提（影响建模的已确认规则）

1. V1 只管理一个羽毛球馆，但仍保留 Venue → Court 结构（避免地址在球场表重复）。
2. 一个活动可对应多个球场，但只有一个**连续时间区间**。
3. `reservation` 是**统一的球场时间占用账本**，既可表示个人预约，也可表示活动占场。
4. 报名占「人」不占「场」：活动有独立的 `max_members`，与球场数量无换算关系。
5. 活动创建即占用球场；活动驳回/取消需释放其占用的 reservation 并处理报名状态。
6. 球场预约以整小时为单位，开放未来 7 个自然日（**含活动占场，见规则 17**）。
7. 同一用户最多拥有 2 个发生时间重叠的**有效个人预约**。
8. 社区只做帖子、评论、点赞；评论只做一级；一用户对同一帖子只能点赞一次。

## 3. 表清单与职责边界

| 表 | 管什么 | 不管什么 |
|---|---|---|
| user | 账号、登录凭据、展示资料、角色 | 不做封禁状态、不做第三方登录 |
| venue | 场馆主数据（名称、地址） | 不做营业时间、不做多馆调度 |
| court | 球场主数据与可否预约 | 不做类型、价格、容量 |
| activity | 活动的组织信息、时间区间、人数上限、生命周期状态 | **不存球场**、不存报名人数、不做草稿态 |
| reservation | 球场 × 时段 × 归属 的占用事实与状态 | 不统计人数、不表达报名 |
| registration | 活动的参与者名册（一人一条，status 表示是否取消） | **不碰球场、不存时段** |
| post | 社区帖子内容 | 不关联活动、不存点赞数（用 COUNT 现算） |
| comment | 帖子的一级评论 | 不做嵌套、不做编辑 |
| post_like | 「用户 × 帖子」的点赞关系 | 无业务状态，无代理主键 |

## 4. 字段方案

### 4.1 user

| 字段 | 类型 | NULL | 默认值 | 说明 |
|---|---|---|---|---|
| id | BIGINT | 否 | 自增 | 主键 |
| username | VARCHAR(64) | 否 | 无 | 登录名，唯一 |
| password_hash | VARCHAR(255) | 否 | 无 | 密码哈希，永不存明文 |
| nickname | VARCHAR(64) | 否 | 无 | 展示昵称 |
| avatar_url | VARCHAR(255) | 是 | NULL | 头像图片路径 |
| role | TINYINT | 否 | 0 | 0=普通用户，1=管理员 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP ON UPDATE | |

### 4.2 venue

| 字段 | 类型 | NULL | 默认值 | 说明 |
|---|---|---|---|---|
| id | BIGINT | 否 | 自增 | 主键（V1 只有一行） |
| name | VARCHAR(64) | 否 | 无 | 场馆名称 |
| address | VARCHAR(255) | 否 | 无 | 地址 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP ON UPDATE | |

### 4.3 court

| 字段 | 类型 | NULL | 默认值 | 说明 |
|---|---|---|---|---|
| id | BIGINT | 否 | 自增 | 主键 |
| venue_id | BIGINT | 否 | 无 | 所属场馆 |
| name | VARCHAR(32) | 否 | 无 | 球场名，如「1 号场」 |
| status | TINYINT | 否 | 1 | 1=可预约，2=不可预约 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP ON UPDATE | |

### 4.4 activity

| 字段 | 类型 | NULL | 默认值 | 说明 |
|---|---|---|---|---|
| id | BIGINT | 否 | 自增 | 主键 |
| creator_id | BIGINT | 否 | 无 | 创建者（组织者），外键 → user.id |
| title | VARCHAR(100) | 否 | 无 | 活动标题 |
| description | VARCHAR(2000) | 是 | NULL | 活动描述 |
| start_time | DATETIME | 否 | 无 | 开始时间 |
| end_time | DATETIME | 否 | 无 | 结束时间（单一连续区间） |
| max_members | INT | 否 | 无 | 报名人数上限，Service 校验 ≥1 |
| status | TINYINT | 否 | 0 | 0=待审核，1=已发布，2=已驳回，3=已取消，4=已结束 |
| reviewer_id | BIGINT | 是 | NULL | 审核人，外键 → user.id，Service 校验其为管理员 |
| review_time | DATETIME | 是 | NULL | 审核时间 |
| reject_reason | VARCHAR(255) | 是 | NULL | 驳回原因 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP ON UPDATE | |

> 活动关联的球场**不存字段**：由 `reservation` 中 `activity_id = 该活动` 的行查询得到（一个活动占 N 块场 = N 行）。

### 4.5 reservation

| 字段 | 类型 | NULL | 默认值 | 说明 |
|---|---|---|---|---|
| id | BIGINT | 否 | 自增 | 主键 |
| court_id | BIGINT | 否 | 无 | 被占用的球场，外键 → court.id |
| user_id | BIGINT | 是 | NULL | 个人预约有值；**活动占场必须为空** |
| activity_id | BIGINT | 是 | NULL | 活动占场有值；**个人预约必须为空** |
| start_time | DATETIME | 否 | 无 | 占用开始（整点） |
| end_time | DATETIME | 否 | 无 | 占用结束（整点） |
| status | TINYINT | 否 | 1 | 1=有效，2=已取消 |
| cancel_time | DATETIME | 是 | NULL | 取消时刻 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 创建（预约/占场）时间 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP ON UPDATE | 最后一次变更时间 |

> **归属不变量（必读）**：`user_id` 与 `activity_id` **至多一者非空**。
> - 个人预约：`user_id` 有值、`activity_id` 为空
> - 活动占场：`activity_id` 有值、`user_id` 为空
>
> 若活动占场行也填 `user_id`，则「同一用户最多 2 个重叠有效个人预约」会被击穿（一个占 4 块场的活动会让创建者瞬间拥有 4 条重叠预约），且「我的预约」会混入活动占用的场。
> **不设 `type` 字段**——归属列本身就是判别依据，额外类型列只会带来不一致。

### 4.6 registration

| 字段 | 类型 | NULL | 默认值 | 说明 |
|---|---|---|---|---|
| id | BIGINT | 否 | 自增 | 主键 |
| user_id | BIGINT | 否 | 无 | 报名用户，外键 → user.id |
| activity_id | BIGINT | 否 | 无 | 报名活动，外键 → activity.id |
| status | TINYINT | 否 | 1 | 1=已报名，2=已取消 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 报名时间 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP ON UPDATE | |

> 一个用户对同一活动**只有一条记录**，取消报名 = 改 status，不新增行、不物理删除。活动创建者由 Service 在创建时自动写入一条「已报名」记录。

### 4.7 post

| 字段 | 类型 | NULL | 默认值 | 说明 |
|---|---|---|---|---|
| id | BIGINT | 否 | 自增 | 主键 |
| user_id | BIGINT | 否 | 无 | 作者，外键 → user.id |
| title | VARCHAR(200) | 否 | 无 | 标题 |
| content | TEXT | 否 | 无 | 正文 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP ON UPDATE | |

> 不存 `like_count`：点赞数以 `post_like` 的行数为准，列表页用 COUNT 现算。

### 4.8 comment

| 字段 | 类型 | NULL | 默认值 | 说明 |
|---|---|---|---|---|
| id | BIGINT | 否 | 自增 | 主键 |
| post_id | BIGINT | 否 | 无 | 所属帖子，外键 → post.id |
| user_id | BIGINT | 否 | 无 | 评论者，外键 → user.id |
| content | VARCHAR(1000) | 否 | 无 | 评论内容 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 一级评论，时序靠它 |

> 无 `update_time`（不支持改评论）、无 `parent_id`（不做嵌套）。

### 4.9 post_like

| 字段 | 类型 | NULL | 默认值 | 说明 |
|---|---|---|---|---|
| post_id | BIGINT | 否 | 无 | 被点赞帖子，复合主键前导列 |
| user_id | BIGINT | 否 | 无 | 点赞用户 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 点赞时间 |

> 无代理主键：点赞是纯关系，唯一性由两端决定，复合主键即为「一人一帖一赞」的 DB 兜底。

## 5. 主键、外键、唯一约束、索引

### PRIMARY KEY

- `user.id`、`venue.id`、`court.id`、`activity.id`、`reservation.id`、`registration.id`、`post.id`、`comment.id`
- `post_like`：**复合主键 `(post_id, user_id)`**（post 在前，因高频查询是「这篇多少赞/谁赞了」）

### FOREIGN KEY

| 子表字段 | 指向 | 删除策略 |
|---|---|---|
| court.venue_id | venue.id | RESTRICT |
| activity.creator_id | user.id | RESTRICT |
| activity.reviewer_id | user.id | RESTRICT |
| reservation.court_id | court.id | RESTRICT |
| reservation.user_id | user.id | RESTRICT |
| reservation.activity_id | activity.id | RESTRICT |
| registration.user_id | user.id | RESTRICT |
| registration.activity_id | activity.id | RESTRICT |
| post.user_id | user.id | RESTRICT |
| comment.post_id | post.id | CASCADE（删帖连带删评论） |
| comment.user_id | user.id | RESTRICT |
| post_like.post_id | post.id | CASCADE（删帖连带删点赞） |
| post_like.user_id | user.id | RESTRICT |

> **activity 永不物理删除**：只能走到已驳回/已取消/已结束状态。

### UNIQUE

| 约束 | 作用 |
|---|---|
| `user.username` | 登录名不重复 |
| `court(venue_id, name)` | 同馆球场名不重复 |
| `registration(user_id, activity_id)` | 一人一活动仅一条报名记录（前提：取消=改状态） |
| `post_like(post_id, user_id)` | 一人一帖只能一赞（由主键承担） |

### INDEX（只列 MVP 真正要走的查询路径）

| 表 | 索引 | 支撑的查询 |
|---|---|---|
| reservation | `(court_id, start_time, end_time)` | 时段冲突检查 |
| reservation | `(user_id, start_time)` | 我的预约 + 「重叠时段最多 2 场」统计 |
| reservation | `(activity_id)` | 活动驳回/取消时批量释放 |
| activity | `(status, start_time)` | 活动列表与审核队列 |
| activity | `(creator_id)` | 我创建的活动 |
| registration | `(activity_id, status)` | 报名人数统计与成员列表 |
| comment | `(post_id, create_time)` | 详情页评论时序 |
| post | `(create_time)` | 首页时间流 |
| post | `(user_id)` | 我的帖子 |

### CHECK（可选，MySQL 8.0.16+ 支持，仅作第二道防线）

- `activity.end_time > activity.start_time`
- `reservation.end_time > reservation.start_time`
- `reservation` 归属恰一非空：`(user_id IS NULL) <> (activity_id IS NULL)`
- 各状态/角色列的值域约束

> CHECK 只兜底，**Service 层仍必须校验**。

## 6. 状态与时间的统一约定

### 状态枚举集合（一次定死，禁止裸数字散落各处）

| 列 | 值 | 默认 |
|---|---|---|
| user.role | 0=普通用户，1=管理员 | 0 |
| court.status | 1=可预约，2=不可预约 | 1 |
| activity.status | 0=待审核，1=已发布，2=已驳回，3=已取消，4=已结束 | 0 |
| reservation.status | 1=有效，2=已取消 | 1 |
| registration.status | 1=已报名，2=已取消 | 1 |

- **activity 无草稿态**：创建即提交审核（与「创建即占场」一致）。
- **activity 无 FULL 态**：满员由报名行数派生，不落状态。
- **reservation 无「已完成」态**：是否过期由 `end_time < 当前时间` 推导。

### 时间约定

- 全部使用 `DATETIME`，日期信息包含在内，不额外拆「日期」字段。
- `activity` 与 `reservation` 的时间重复是**有意冗余**：账本行必须自洽，查冲突时不能每行 join Activity。
- 对应不变量：**活动占场行的 start/end 必须等于 activity 的 start/end**；活动若允许改时间，必须级联更新其 reservation 行并重新检查冲突。
- 预约时间以整小时为单位，是否允许跨天（如 23:00–01:00）由 Service 层统一判定。

## 7. 数据库兜底 vs Service 层规则

### 由数据库约束兜底

- 登录名、同馆球场名、一人一活动一条报名、一人一帖一赞
- 外键完整性、NOT NULL、字段默认值
- 简单值域与关系（CHECK，可选）

### 只能由 Service 层保证（写进设计约束，不要指望加字段解决）

| 规则 | 为什么 DB 兜不了 |
|---|---|
| 同一球场同时段不冲突 | MySQL 无排除约束（exclusion constraint），需区间相交查询 + 事务/行锁 |
| 同一用户重叠时段有效个人预约 ≤2 | 跨行跨时段统计；且只统计 `activity_id IS NULL` 的行 |
| 预约开放未来 7 个自然日（含活动占场） | 时间窗口判断；否则可借「创建远期活动」绕过 |
| 开场前 4 小时内普通用户不得自行取消个人预约 | 时间窗口判断 |
| 活动创建时写入 N 行 reservation（N=球场数）+ 创建者报名行 | 跨表写入，需同一事务 |
| 活动驳回/取消 → 释放其全部 reservation + 报名置为已取消 | 跨表状态级联，需同一事务 |
| 满员判定与并发防超卖 | 计数竞态，需事务/行锁 |
| 只有「已发布」活动可报名 | 业务状态前置条件 |
| 活动占场行 `user_id` 必须为空、两归属恰一非空 | 业务不变量 |
| 活动占场行时间 == 活动时间；改时间需级联 | 冗余字段的一致性 |
| 球场 `status=不可预约` 时禁止写入 reservation | 业务前置条件 |
| `reviewer_id` 必须为管理员角色 | 角色校验 |
| 时间整点对齐、跨天策略 | 格式与业务策略 |

## 8. MVP 设计边界（明确不做）

**不加的表**：审计日志、操作日志、消息通知、等待名单/候补、文件/图片、权限表、营业时间表、时段预生成表、活动分类标签、关注关系、私信、支付订单、积分会员、数据字典。

**不加的字段**：

- 软删除字段（deleted / is_deleted）：活动走状态，内容走物理删除
- `version` 乐观锁：并发由事务与行锁解决
- `post.like_count` 及任何冗余计数
- `comment.parent_id`、`comment.update_time`
- `post.status` / `comment.status`：违规内容处理 = 删除，不做下架
- `reservation.cancel_source`、`reservation` 的「已完成/过期」态
- `user` 封禁状态、手机号/邮箱、第三方登录字段
- `court` 类型/价格/容量；`venue` 营业时间
- `activity` 报名截止时间、封面、水平分级、分类标签
- `post` 关联活动的字段

**不做的架构动作**：多场馆调度（保留 venue 只为不重复存地址）、分库分表、读写分离、缓存层、消息队列、搜索引擎——单体 Spring Boot + 单库足够。

## 9. 原「待确认问题」收口结论

| 原问题 | 结论 |
|---|---|
| 各实体字段与类型 | 见第 4 节 |
| Reservation 与 Activity 时间如何建模 | 各自独立存 start/end；活动占场行与活动时间保持一致（有意冗余 + 不变量） |
| Registration 状态历史如何保存 | 不保留历史：一人一活动一条记录，status 表示是否取消 |
| PostLike 唯一约束 | 复合主键 `(post_id, user_id)` |
| 球场时间冲突如何落地 | Service 层区间相交查询 + 事务/行锁；MySQL 无法用唯一索引兜底 |
| 用户重叠时间最多两个预约如何落地 | Service 层，仅统计 `activity_id IS NULL` 的有效行 |
| 软删除策略 | 不做软删除 |
| 索引策略 | 见第 5 节 |
