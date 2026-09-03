# 架构设计

## 当前阶段

本文件是架构设计占位文档。详细方案在数据库和 API 设计完成后补充。

## 计划架构

第一版采用单体 Spring Boot 后端：

```text
Client
  ↓
Controller
  ↓
Service
  ↓
Mapper
  ↓
MySQL
```

横向基础设施：

```text
Interceptor
Global Exception Handler
Validation
Logging
Redis（按业务需要引入）
```

## 设计原则

- 业务优先
- 分层职责清晰
- 不为技术而技术
- 先保证正确性，再考虑性能
- 核心业务规则必须有后端校验
- 重要设计决策记录在 `docs/decisions.md`
