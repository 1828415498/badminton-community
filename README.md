# Badminton Community（羽毛球社区）

面向大学生羽毛球爱好者的找球、组局与场地预约平台。项目采用前后端分离的单体架构，目前已实现主要 MVP 业务代码，仍在补充关键场景验证和工程化交付。开发过程使用 AI 辅助；业务规则、技术取舍与验收以仓库中的实现和测试为准。

## 已有功能

- 用户注册、登录、个人资料；普通用户与管理员两种角色。登录使用 JWT，密码以 BCrypt 哈希存储。
- 查看场馆、球场及某天的占用情况；创建、查询和取消个人场地预约。
- 创建球局活动并占用球场；管理员审核通过或驳回；创建者或管理员取消活动。驳回、取消时释放相关占场并作废报名；定时任务维护已结束状态。
- 活动报名、取消报名、查看成员及“我的报名”；有效报名人数受活动人数上限约束。
- 社区发帖、评论、点赞/取消点赞，以及管理员删除帖子；图片上传。
- Vue 前端已有首页、活动、场馆预约、社区和个人中心等页面。

`reservation` 表统一记录个人预约与活动占场；预约和报名的最终业务判断在 MySQL 事务中完成。已有个人同场争抢、活动与个人争抢（含双球场活动）、两个多球场活动部分重叠、报名最后名额，以及同一成员取消与重报时另一成员争名额的并发回归测试。这些测试不保证覆盖所有线程调度和数据库交错。

## 技术栈与当前边界

| 部分 | 当前实现 |
|---|---|
| 后端 | Java 17、Spring Boot 3.3.5、MyBatis、MySQL、JWT |
| 前端 | Vue 3、Vite、Pinia、Axios、Element Plus |
| 数据库 | MySQL 8.0.16+；`sql/schema.sql` 定义 9 张业务表 |
| 测试 | JUnit / Spring Boot Test；覆盖预约、活动占场、报名及事务回滚等关键场景 |

Redis、MQ、ES 和分布式锁**尚未接入**。仓库提供 Docker Compose 本地部署配置；容器运行验证状态见[部署说明](docs/deployment.md)。现阶段不使用 Redis 缓存结果或消息队列代替 MySQL 对场地冲突、报名名额的最终判定。

## 本地运行

准备 JDK 17、MySQL 8.0.16+、Node.js 22.12+ 与 npm。以下命令以 Windows PowerShell、仓库根目录为例。

1. 启动 MySQL，按顺序执行 `sql/schema.sql`、`sql/views.sql` 和 `sql/data.sql`。初始化数据包含开发用管理员、1 家场馆及 6 块球场；详情见 [SQL 说明](sql/README.md)。
2. 在启动后端的 PowerShell 终端设置本机数据库密码，并生成本次运行使用的 JWT 密钥。仓库的[后端配置](src/main/resources/application.yml)不保存这两个值：

   ```powershell
   $env:DB_PASSWORD = "<本机 MySQL 密码>"
   $jwtBytes = [byte[]]::new(32)
   $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($jwtBytes)
   $rng.Dispose()
   $env:APP_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
   ```

   默认连接本机 `badminton_community` 库，用户名为 `root`。连接地址、用户和上传目录可分别通过 `DB_URL`、`DB_USER`、`APP_UPLOAD_DIR` 覆盖；变量详情见[部署说明](docs/deployment.md)。重启后端时须继续使用同一 JWT 密钥，否则此前签发的令牌会失效。
3. 构建并启动后端：

   ```powershell
   .\mvnw.cmd -DskipTests package
   java -jar target/backend-0.0.1-SNAPSHOT.jar
   ```

   若受限 Windows 终端把 Maven 用户目录错误识别为 `C:\`，并报无法创建 `C:\.m2\repository`，可先在该终端执行 `$env:MAVEN_OPTS = "-Duser.home=$env:USERPROFILE"`，再运行 `mvnw.cmd`。正常环境不需要这一步。

4. 另开终端启动前端：

   ```powershell
   cd frontend
   npm ci
   npm run dev
   ```

后端默认使用 `http://localhost:8080`，前端开发服务默认使用 `http://localhost:5173`，Vite 将 `/api` 请求代理到后端。SQL 初始化的管理员账号只供本地演示，首次部署前务必修改密码。

运行自动化测试需先启动 MySQL，并在运行测试的 PowerShell 终端显式设置测试库账号和密码：

```powershell
$env:TEST_DB_USER = "<测试库 MySQL 用户>"
$env:TEST_DB_PASSWORD = "<测试库 MySQL 密码>"
.\mvnw.cmd test
```

[测试配置](src/test/resources/application-test.yml)固定连接独立的 `badminton_community_test` 库，不读取后端运行用的 `DB_USER` / `DB_PASSWORD`。测试启动会重建该库的业务表和视图，账号需有建库、建表、删表和建视图权限；不要使用开发库或共享库。测试专用 JWT 密钥仅用于测试上下文。

## Docker Compose 本地部署

Compose 编排前端、后端和 MySQL，并用独立卷保存数据库和上传文件。初始化方式、密钥设置、验证和数据保留规则见[部署说明](docs/deployment.md)。此部署仅绑定本机 `127.0.0.1`，不包含公网 HTTPS 配置。

## 当前待完善事项

- 按实际业务风险继续验证尚未覆盖的并发交错和更多活动多场地组合；现有同时起跑测试不保证每一种先后顺序都执行到。
- 继续补充容器环境中活动占场与个人预约交错的验证；当前容器验收覆盖顺序预约、重启持久化和上传文件。
- 根据实际读取热点和通知需求，再决定是否引入 Redis、MQ 等组件；不把计划中的技术写成已完成能力。
- 同步过时文档与真实代码，避免设计稿被误认为当前实现。

## 项目文档

- [需求分析](docs/requirements.md) · [架构设计](docs/architecture.md) · [数据库设计](docs/database.md) · [API 设计](docs/api.md)
- [技术决策记录](docs/decisions.md) · [开发工作流](docs/development.md) · [部署文档](docs/deployment.md) · [AI 开发规范](docs/ai-development.md)

本机学习计划与进度记录被 Git 忽略，不作为公开仓库的项目文档。

部分设计文档包含后续规划；判断功能是否已实现，以当前代码及验证结果为准。
