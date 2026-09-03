---
description: API 设计与 Review 规则
alwaysApply: false
enabled: true
---

# API Review Rules

API 应从业务用例和资源模型推导。

每个 API 至少明确：
- HTTP Method
- URL
- 用途
- Auth
- Authorization
- Path 参数
- Query 参数
- Request Body
- Response
- 错误场景

优先保持 REST 风格与命名一致。

统一响应结构、异常码和分页格式必须在项目范围内保持一致。
