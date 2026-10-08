# SQL

数据库初始化脚本（依据 `docs/database.md` MVP 定稿版）。

## 执行顺序

1. `schema.sql` —— 建库 `badminton_community` + 9 张表（含约束与索引）
2. `views.sql` —— 建 4 个视图（只保存查询定义，不改任何表结构，可重复执行）
3. `data.sql` —— 初始化数据：管理员账号、1 家场馆、6 块球场

> `views.sql` 与 `data.sql` 之间无依赖，先后皆可；但它必须在 `schema.sql` 之后执行。

## 视图清单（`views.sql`）

| 视图 | 用途 | 为什么需要 |
|---|---|---|
| `v_reservation_detail` | 场地占用台账明细 | `reservation` 三端全是外键，直查只有编号；视图 JOIN 出场馆/球场/占用方，并把 `user_id` 与 `activity_id` 的二选一语义翻译成 `source_type` |
| `v_activity_signup_detail` | 活动报名明细 | `registration` 是「报名」这个多对多联系落成的表，视图补齐活动标题、报名人昵称与双方状态文本 |
| `v_post_with_stats` | 帖子 + 作者 + 评论数 + 点赞数 | 评论数/点赞数是**导出项**，放视图实时 COUNT，不在 `post` 表建列 |
| `v_activity_signup_stat` | 活动报名进度（有效/已取消/剩余名额） | 已报名人数是**导出项**，放视图保证与 `registration` 表永远一致，避免计数字段与报名表脱钩 |

`views.sql` 末尾附了直接可跑的验证查询，其中两条是**预期返回 0 行的校验**：视图算出的报名数与报名表实际行数的一致性校验、以及剩余名额为负（超卖）的边界校验。

## 说明

- 环境要求：MySQL 8.0.16+（CHECK 约束依赖该版本）。
- 管理员账号：`admin123 / 123456`（BCrypt 哈希入库）。**仅限开发环境，上线前必须改密**。
- 日常只重灌数据：执行 `data.sql` 前需先清空相关表；结构变更只动 `schema.sql`。
- 若在非全新库执行，注意 `data.sql` 依赖 venue.id 自增从 1 开始。
- 视图不存数据，改视图定义只需重跑 `views.sql`（用的是 `CREATE OR REPLACE VIEW`）。
