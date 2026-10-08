# Badminton Community（羽毛球社区）

面向大学生羽毛球爱好者的找球、组局与场地预约平台。项目采用前后端分离的单体架构，目前已实现主要 MVP 业务代码，仍在补充关键场景验证和工程化交付。开发过程使用 AI 辅助；业务规则、技术取舍与验收以仓库中的实现和测试为准。

## 已有功能

- 用户注册、登录、个人资料；普通用户与管理员两种角色。登录使用 JWT，密码以 BCrypt 哈希存储。
- 查看场馆、球场及某天的占用情况；创建、查询和取消个人场地预约。
- 创建球局活动并占用球场；管理员审核通过或驳回；创建者或管理员取消活动。驳回、取消时释放相关占场并作废报名；定时任务维护已结束状态。
- 活动报名、取消报名、查看成员及“我的报名”；有效报名人数受活动人数上限约束。
- 社区发帖、评论、点赞/取消点赞，以及管理员删除帖子；图片上传。
- Vue 前端已有首页、活动、场馆预约、社区和个人中心等页面。

`reservation` 表统一记录个人预约与活动占场；预约和报名的最终业务判断在 MySQL 事务中完成。已有个人预约同场同时段并发争抢的回归测试，不能据此推断所有活动和报名并发场景都已验证。

## 技术栈与当前边界

| 部分 | 当前实现 |
|---|---|
| 后端 | Java 17、Spring Boot 3.3.5、MyBatis、MySQL、JWT |
| 前端 | Vue 3、Vite、Pinia、Axios、Element Plus |
| 数据库 | MySQL 8.0.16+；`sql/schema.sql` 定义 9 张业务表 |
| 测试 | JUnit / Spring Boot Test；现有自动化测试主要覆盖个人预约与时间规则 |

Redis、MQ、ES、分布式锁和 Docker 一键部署**尚未接入**。它们是后续按具体业务问题评估的能力，不属于当前已实现功能。现阶段不使用 Redis 缓存结果或消息队列代替 MySQL 对场地冲突、报名名额的最终判定。

## 本地运行

准备 JDK 17、MySQL 8.0.16+、Node.js 与 npm。以下命令以 Windows PowerShell、仓库根目录为例。

1. 启动 MySQL，按顺序执行 `sql/schema.sql` 和 `sql/data.sql`。初始化数据包含开发用管理员、1 家场馆及 6 块球场；详情见 [SQL 说明](sql/README.md)。
2. 检查 [后端配置](src/main/resources/application.yml) 中的数据库连接。仓库里的数据库凭据和 JWT 密钥仅供本地开发使用，部署到公开环境前必须替换，不能沿用示例值。
3. 构建并启动后端：

   ```powershell
   .\mvnw.cmd -DskipTests package
   java -jar target/backend-0.0.1-SNAPSHOT.jar
   ```

4. 另开终端启动前端：

   ```powershell
   cd frontend
   npm ci
   npm run dev
   ```

后端默认使用 `http://localhost:8080`，前端开发服务默认使用 `http://localhost:5173`，Vite 将 `/api` 请求代理到后端。SQL 初始化的管理员账号只供本地演示，首次部署前务必修改密码。

运行自动化测试需先启动 MySQL；测试配置连接独立的 `badminton_community_test` 数据库，不应指向开发库。运行 `.\mvnw.cmd test` 前请核对 [测试配置](src/test/resources/application-test.yml)。

## 当前待完善事项

- 补齐活动占场与个人预约竞争、报名最后名额等关键场景的验证。
- 统一部署配置与敏感信息管理，完成可复现的部署流程。
- 根据实际读取热点和通知需求，再决定是否引入 Redis、MQ 等组件；不把计划中的技术写成已完成能力。
- 同步过时文档与真实代码，避免设计稿被误认为当前实现。

## 项目文档

- [需求分析](docs/requirements.md) · [架构设计](docs/architecture.md) · [数据库设计](docs/database.md) · [API 设计](docs/api.md)
- [技术决策记录](docs/decisions.md) · [开发工作流](docs/development.md) · [部署文档](docs/deployment.md) · [AI 开发规范](docs/ai-development.md)
- [学习与开发计划](docs/study-development-plan.md) · [当前学习进度](docs/study-progress.md)

部分设计文档包含后续规划；判断功能是否已实现，以当前代码及验证结果为准。
