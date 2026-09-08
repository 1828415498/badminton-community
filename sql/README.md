# SQL

数据库初始化脚本（依据 `docs/database.md` MVP 定稿版）。

## 执行顺序

1. `schema.sql` —— 建库 `badminton_community` + 9 张表（含约束与索引）
2. `data.sql` —— 初始化数据：管理员账号、1 家场馆、6 块球场

## 说明

- 环境要求：MySQL 8.0.16+（CHECK 约束依赖该版本）。
- 管理员账号：`admin123 / 123456`（BCrypt 哈希入库）。**仅限开发环境，上线前必须改密**。
- 日常只重灌数据：执行 `data.sql` 前需先清空相关表；结构变更只动 `schema.sql`。
- 若在非全新库执行，注意 `data.sql` 依赖 venue.id 自增从 1 开始。
