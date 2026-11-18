# 项目进度

更新日期：2026-11-18
设计文档版本：v0.18
当前阶段：阶段 2 + 生产化收尾完成；阶段 3 待开始（先读 `docs/stage-3-handoff.md`）

## 项目定位（护栏）

- 定位：简历项目 + 第一方统一认证服务；单次部署、内部模块化单体，不拆微服务
- 只做身份：注册、登录、登出、改密、验证码、注销/恢复、封禁、平台管理员
- 业务角色/团队/空间/项目权限由各业务项目自己维护，user-center 不提供
- 非目标（本期）：OAuth2/OIDC、RS256、refresh token、内部微服务拆分、真正跨应用 SSO
- 令牌：JWT 含 `sub/ver/aud/jti/iat/exp/iss`，2 小时，不放 role；角色以数据库为准
- Redis：只用于验证码、发码限频、登录失败锁定；不缓存 introspection

## 已完成

- 阶段 0/1：工程骨架、统一异常、JWT 鉴权链、注册/登录/登出/改密/验证码重置、Testcontainers
- 阶段 2：账号/IP 登录锁定、邮箱/IP 发码限流、验证码失败 5 次作废、Top 1000 弱密码、可信 `remoteAddr`
- 生产化收尾（2026-11-18）：
  - 错误响应改 RFC 9457 `ProblemDetail`；成功响应直接返回 DTO；405/406/415 统一映射
  - JWT 固定 HS256、校验 audience；Bearer 大小写不敏感；401 带 `WWW-Authenticate`、429 带 `Retry-After`
  - `User` 的 `toString` 排除密码哈希；`/users/me` 改用列投影
  - Flyway 迁移：`db/migration/V1__init.sql`；开发种子在 `db/dev/R__seed_admin.sql`，生产无默认账号
  - 邮件异步投递（有界线程池，队列满回退调用线程）；`EmailDispatchService` 记录失败日志
  - MySQL 集成测试用 `@ServiceConnection`；集成测试移交 Failsafe；安全测试依赖改用 Boot 4 模块化 starter
  - 配置收口为 `MailProperties`/`CorsProperties`/`TimeZoneProperties`；`requestCache` 关闭
  - Nginx 80→443、MySQL/Redis 不发布宿主机端口、应用健康检查、Dependabot、README
- 测试：94 个单元/slice + 8 个集成（Failsafe）；覆盖率行 86.0%、分支 80.7%，门槛 80%

## 环境

- 本机：Windows 11、JDK 21（`D:\dev\jdk-21`）、Node 24；本机无 Docker
- 开发虚拟机 `ssh user-center-vm`（192.168.216.130）：Docker 29.9.2
  - MySQL 8.4.13、Redis 7.4；开发启动：`docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis`
  - 生产只启动 `docker compose up -d`，Nginx 使用 `deploy/nginx/certs` 下的证书
- 种子管理员：`admin0001` / `admin@user-center.local`，仅 dev profile 创建，密码见本地 `docs/decisions.md`
- 部署：云服务器（已有，规格待确认；建议 2 核 4G）

## 验收结果

- 本机 `./mvnw verify`：94 个单元/slice 通过 + 8 个集成用例由 Failsafe 执行（无 Docker 跳过），Spotless、Checkstyle、JaCoCo 通过
- VM Docker（2026-11-18）：Flyway 成功应用 V1；`AuthFlowIntegrationTest` 8/8 通过
- 账号锁定、IP 锁定、验证码 5 次作废、弱密码不消费验证码、令牌版本/状态失效、枚举响应一致均已验证

## 下一步（阶段 3 管理员）

1. 分页查询用户：`page`/`size` 上限 100，邮箱掩码，不返回密码哈希
2. 封禁/解封：按 `user_account` 操作，自增 `token_version`；禁止管理员封禁自己
3. 管理员重置密码：生成随机密码，仅响应一次，自增 `token_version`
4. 管理员操作审计：登录、封禁/解封、重置密码、角色变更；为审计表和分页查询加 Flyway V2
5. 测试：管理员流程、普通用户 403、目标不存在 404、封禁后旧令牌失效、分页边界

## 交接

- 新会话先读本文件与 `docs/stage-3-handoff.md`，再按需读 `docs/plan.md` §7、§8、§11
- 阶段 3 改动认证/账号状态后，在 VM Docker 跑 `mvn -B -Dit.test=AuthFlowIntegrationTest verify`
- Git：阶段 2 与本次生产化改动已合并到 `main`；`CLAUDE.md` 主工作区仍有未提交裁剪
- 课程文件在 `D:\teach\user-center-spring-security`（本地，不提交仓库）
- 关键约束：JWT 不含 role；改密/登出/封禁/恢复/角色变更/管理员重置必须自增 `token_version`

## 待确认 / 阻塞

- 阶段 3 管理员接口尚未实现；范围与开放决策见 `docs/stage-3-handoff.md`
- 后续优化：Lua 原子化 `INCR + EXPIRE`、验证码 `GET + DEL`（见 `docs/decisions.md` §28）
- SMTP 已实测（QQ 邮箱）；正式上线建议企业邮件 + SPF/DKIM/DMARC
- 云服务器规格未定；前端 `web/` 尚未实现（阶段 5），Nginx TLS 证书需部署时生成或申请
- CI 工作流草案在 `docs/ci-workflow.yml`；当前 PAT 缺少 `workflow` scope，授权后复制到 `.github/workflows/ci.yml` 启用
