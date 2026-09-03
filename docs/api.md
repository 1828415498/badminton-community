# API 设计

## 当前状态

**待数据库模型和业务流程冻结后设计。**

每个 API 至少记录：

- HTTP Method
- URL
- 用途
- 认证要求
- 权限要求
- Path 参数
- Query 参数
- Body
- 成功响应
- 业务错误

## API 模块草案

- `/auth`：注册、登录、退出
- `/users`：个人资料
- `/activities`：活动
- `/reservations`：场地预约
- `/registrations`：活动报名
- `/posts`：社区帖子
- `/comments`：评论
- `/likes`：点赞
- `/admin`：管理员能力

具体路径暂不冻结。
