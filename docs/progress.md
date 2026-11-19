# 项目进度

更新日期：2026-11-19
设计文档版本：v0.19
当前阶段：阶段 3 管理员完成（本机 + VM）；阶段 4 待开始

## 项目定位（护栏）

- 定位：简历项目 + 第一方统一认证服务；单次部署、内部模块化单体，不拆微服务
- 只做身份：注册、登录、登出、改密、验证码、注销/恢复、封禁、平台管理员
- 业务角色/团队/空间/项目权限由各业务项目自己维护，user-center 不提供
- 非目标（本期）：OAuth2/OIDC、RS256、refresh token、内部微服务拆分、真正跨应用 SSO
- 令牌：JWT 含 `sub/ver/aud/jti/iat/exp/iss`，2 小时，不放 role；角色以数据库为准
- Redis：只用于验证码、发码限频、登录失败锁定；不缓存 introspection

## 已完成

- 阶段 0/1：工程骨架、统一异常、JWT 鉴权链、注册/登录/登出/改密/验证码重置
- 阶段 2：账号/IP 锁定、邮箱/IP 发码限流、验证码失败 5 次作废、Top 1000 弱密码、可信 `remoteAddr`
- 生产化（2026-11-18）：ProblemDetail、JWT HS256+aud、Flyway、异步邮件、Failsafe、TLS/CI 配置、依赖扫描
- 阶段 3 管理员（2026-11-19）：
  - `get /api/v1/admin/users`：`page>=1`、`size<=100`、可选 `status`；只取展示列，邮箱掩码
  - `put /api/v1/admin/users/{account}/status`：封禁/解封；状态 + `token_version` 原子更新；禁止封禁自己；状态不变幂等
  - `post /api/v1/admin/users/{account}/password/reset`：16 位随机密码，仅返回一次，`token_version` 自增
  - Flyway `V2__admin_indexes.sql`；管理端按 `user_account` 查询；普通用户访问 403
  - 默认决策：已注销账号不出现在列表、不可操作；可封禁其他管理员；管理员重置后不强制改密
- 测试：113 个单元/slice + 9 个集成（AdminFlow 1 + AuthFlow 8，Failsafe）；覆盖率行 87.6%、分支 82.0%，门槛 80%

## 环境

- 本机：Windows 11、JDK 21（`D:\dev\jdk-21`）、Node 24；本机无 Docker
- 开发虚拟机 `ssh user-center-vm`（192.168.216.130）：Docker 29.9.2
  - MySQL 8.4.13、Redis 7.4；开发启动：`docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis`
  - 生产只启动 `docker compose up -d`，Nginx 使用 `deploy/nginx/certs` 下的证书
- 种子管理员：`admin0001` / `admin@user-center.local`，仅 dev profile 创建，密码见本地 `docs/decisions.md`
- 部署：云服务器（已有，规格待确认；建议 2 核 4G）

## 验收结果

- 本机 `./mvnw verify`：113 个单元/slice 通过 + 9 个集成用例由 Failsafe 执行（无 Docker 跳过），Spotless、Checkstyle、JaCoCo 通过
- VM Docker（2026-11-19）：Flyway V1+V2 成功；`AuthFlowIntegrationTest` 8/8、`AdminFlowIntegrationTest` 1/1 通过
- 阶段 2：账号锁定、IP 锁定、验证码 5 次作废、弱密码不消费验证码、令牌失效、枚举一致均已验证
- 阶段 3 本机：单元 + MockMvc 覆盖分页、掩码、封禁/解封、自封、随机密码、401/403/400

## 下一步（阶段 4 注销与恢复）

1. `delete /api/v1/users/me`：逻辑注销，自增 `token_version`；管理员不可自助注销
2. `post /api/v1/users/me/recover`：邮箱验证码 + 新密码，恢复后自增 `token_version`
3. 状态语义：注销后登录 40104；恢复场景验证码 `verify:recover`；封禁账号不可恢复
4. 阶段 3 补测：VM Docker 跑 `AdminFlowIntegrationTest`；管理员操作审计表（V3）留作后续
5. 测试：注销/恢复闭环、旧令牌失效、验证码场景隔离、管理员接口不操作已注销账号

## 交接

- 新会话先读本文件与 `docs/stage-4-handoff.md`，再按需读 `docs/plan.md` §7、§8、§11
- 阶段 4 改动账号状态后，在 VM Docker 跑 `mvn -B -Dit.test=AdminFlowIntegrationTest,AuthFlowIntegrationTest verify`
- Git：阶段 3 已合回 `main`；`CLAUDE.md` 已提交；工作区应以 `git status` 为准
- 课程文件在 `D:\teach\user-center-spring-security`（本地，不提交仓库）
- 关键约束：JWT 不含 role；改密/登出/封禁/恢复/角色变更/管理员重置必须自增 `token_version`

## 待确认 / 阻塞

- **GitHub Actions 被账号级禁用**：Support 工单 #5026657 等待回复；workflow 已在 `.github/workflows/ci.yml`
- 管理员操作审计表尚未实现（建议阶段 4 一并做 Flyway V3）
- 后续优化：Lua 原子化 `INCR + EXPIRE`、验证码 `GET + DEL`（见 `docs/decisions.md` §28）
- 云服务器规格未定；前端 `web/` 尚未实现（阶段 5），Nginx TLS 证书需部署时生成或申请
