# 部署与运行配置

## Docker Compose 本地部署

仓库根目录的 `compose.yaml` 启动 MySQL、Spring Boot 后端和 Nginx 前端。浏览器从同一地址访问页面、`/api` 和 `/api/files`。MySQL 只在 Compose 内部网络监听；前端默认只映射到本机 `127.0.0.1:8088`。此配置用于本机复现和演示，不包含公网 HTTPS、备份或生产监控。

在仓库根目录用 PowerShell 生成本机 `.env`（已被 Git 忽略）。以下命令每次生成不同的数据库密码和 JWT 密钥；已有 `.env` 时不要重跑，以免让现有 MySQL 卷中的账号密码与配置不一致：

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
function New-Secret {
    $bytes = [byte[]]::new(32)
    $rng.GetBytes($bytes)
    [Convert]::ToBase64String($bytes)
}
@(
    "MYSQL_ROOT_PASSWORD=$(New-Secret)"
    "MYSQL_PASSWORD=$(New-Secret)"
    "APP_JWT_SECRET=$(New-Secret)"
    'APP_PORT=8088'
) | Set-Content -Encoding ascii .env
$rng.Dispose()
```

启动并等待三个服务：

```powershell
docker compose up --build -d --wait
docker compose ps
```

访问 `http://127.0.0.1:8088`，或检查 `http://127.0.0.1:8088/api/venues`。首次创建空的 MySQL 卷时，镜像按 `schema.sql`、`views.sql`、`data.sql` 顺序初始化；示例管理员 `admin123 / 123456` 仅用于本机演示。请勿把该默认账号直接暴露在公网。

MySQL 数据保存在 `mysql_data` 卷，图片保存在 `uploads_data` 卷。可以登录后通过“个人中心”上传图片，再执行 `docker compose restart`，检查场馆、登录及图片 URL 仍可访问。`docker compose down` 仅停止并删除容器，保留这两个卷；`docker compose down --volumes` **会删除业务数据和上传文件**，不要用于保留数据的环境。数据库初始化 SQL 只会在空卷首次启动时运行，后续结构修改需要单独迁移，不会自动重灌。

预约主流程也可以按以下顺序验收：注册两个普通账号，选明天的一块空闲球场和整点 1 小时时段；账号 A 创建预约，账号 B 创建同一时段应得到 `COURT_ALREADY_RESERVED`；检查 A 的预约凭证；重启服务后确认 A 的“我的预约”仍有该记录且 B 仍被拒绝；A 取消后 B 应能预约，球场占用查询最终只应有一条该时段的有效占用。请在独立演示库执行，避免往真实业务库写入验收账号和预约。

查看问题：`docker compose logs --tail=100 db backend frontend`。停止：`docker compose down`。Compose 中的前端只监听本机；如需公网访问，另行配置 HTTPS、域名、网络访问限制和独立管理员密码。

## 后端环境变量

直接运行后端 JAR 时使用以下变量；Compose 会为容器设置数据库地址、账号、密码和上传目录。

| 变量 | 用途 | 未设置时 |
|---|---|---|
| `DB_URL` | MySQL JDBC URL | 本机 `localhost:3306/badminton_community` |
| `DB_USER` | MySQL 用户名 | `root`，仅供本地开发 |
| `DB_PASSWORD` | MySQL 密码 | 空值；需要密码的数据库无法连接 |
| `APP_JWT_SECRET` | JWT 签名密钥 | 启动失败；UTF-8 编码后至少 32 字节 |
| `APP_UPLOAD_DIR` | 图片存储目录 | `./uploads`，相对于启动目录 |
| `SERVER_PORT` | HTTP 端口 | `8080` |

本地 PowerShell 设置示例见 [README](../README.md#本地运行)。重启后端时须保留同一 JWT 密钥，否则之前签发的令牌会失效。自动化测试使用独立的 `badminton_community_test` 库及测试专用签名密钥；运行前须设置 `TEST_DB_USER`、`TEST_DB_PASSWORD`（参见 [README](../README.md)）。测试引导程序会重建测试库业务表和视图，不要指向开发库或共享库。

## 已验证范围与边界

2026-10-15 在 Windows Docker Desktop 的独立 Compose 项目 `badminton-community-verification` 中，从空卷构建并启动三个服务。`docker compose config --quiet`、后端 JAR 构建、前端生产构建均通过；MySQL 初始化后查到 6 块球场和 4 个视图。通过前端统一入口访问场馆接口、注册登录和上传图片；重启三个服务后，同一账号仍可登录，图片 URL 返回与上传前 SHA-256 相同的内容。这不等于公网生产部署验证。首次构建曾有循环依赖、包体积及依赖审计告警；后续处理情况见下文。

同日继续在该独立项目中通过 `127.0.0.1:8088/api` 验收个人预约：2026-10-16 10:00–11:00 的 1 号场，账号 A 创建的预约编号为 1；账号 B 同时段请求返回 `COURT_ALREADY_RESERVED`；凭证视图显示 1 小时。重启三个服务后，A 的预约仍在“我的预约”中且 B 仍被拒绝；A 取消后 B 创建编号 2 的预约成功，球场占用接口仅返回该时段 1 条有效记录。此结果验证了部署环境中的顺序预约、冲突、取消和持久化链路；**未进行容器内并发争抢或 Vue 页面交互验收**。

2026-10-15 核查前端告警：`ActivityRow` 和 `GameRow` 不再通过导出自身的组件统一出口导入同级组件，生产构建的循环依赖警告已消失。当时 `package-lock.json` 将传递依赖 `source-map-js` 从 `1.2.1` 更新至修复版 `1.2.2`；官方 npm registry 审计为 0 项，前端生产构建和 Docker 前端镜像构建通过。

2026-10-27 重新核查时，新增的安全公告让旧锁文件出现 5 项高危审计告警。锁文件内的 Vite、Puppeteer、`follow-redirects` 及其传递依赖更新后，官方 npm registry 干净安装、前端生产构建和审计均通过，审计为 0 项；Puppeteer 可以启动本机 Chrome。**超过 500 KB 的分包提示仍存在**，本轮没有测量页面加载性能。Nginx 的后端代理改为通过 Docker DNS 动态解析服务名，以适应后端容器重建；本机 Docker Desktop 4.89.0 因启动时无法处理旧的 `sailor-ingest.sock` 文件而未能启动，所以这项代理改动和更新后的容器镜像在 10 月 27 日尚未做运行验收，不能沿用 10 月 15 日的镜像构建结果声称它们已验证。
