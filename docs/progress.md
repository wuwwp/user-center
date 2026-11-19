# 项目进度

更新日期：2026-11-19
设计文档版本：v0.20
当前阶段：阶段 4 注销/恢复与管理员审计完成（本机 + CI）；阶段 5 Web 前端待开始

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
- 阶段 3 管理员（2026-11-19）：分页查询、封禁/解封、随机密码重置；管理端 403；封禁/重置自增 `token_version`
- 阶段 4 注销与恢复（2026-11-19）：
  - `delete /api/v1/users/me`：要求新场景 `DELETE` 验证码；逻辑注销 `is_delete=1` + `token_version` 自增；管理员自助注销返回 403
  - `post /api/v1/users/me/recover`：`RECOVER` 验证码 + 新密码；原子恢复 `is_delete=0`、`password_hash`、`token_version+1`；封禁账号拒绝；清账号维度登录失败计数
  - 注销只允许 `isActive()` 账号；重复注销在 Service 层按幂等成功处理；旧令牌重试会在过滤器得到 401
  - 忘记密码重置成功后也清账号维度失败计数；IP 计数保留，靠 TTL 衰减
  - `EmailScene.DELETE` 与 `verify:delete:{email}`；验证码场景隔离，RESET 码不能用于注销
  - Flyway `V3__admin_audit_log.sql`：管理员封禁/解封/重置与审计写入同一事务
- 测试：130 个单元/slice + 13 个集成（AdminFlow 2 + AuthFlow 8 + AccountLifecycle 3）；覆盖率门槛 80% 通过（本机单元/slice 行 89.6%、分支 80.2%）

## 环境

- 本机：Windows 11、JDK 21（`D:\dev\jdk-21`）、Node 24；本机无 Docker
- 开发虚拟机 `ssh user-center-vm`（192.168.216.130）：Docker 29.9.2
  - MySQL 8.4.13、Redis 7.4；开发启动：`docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis`
  - 生产只启动 `docker compose up -d`，Nginx 使用 `deploy/nginx/certs` 下的证书
- 种子管理员：`admin0001` / `admin@user-center.local`，仅 dev profile 创建，密码见本地 `docs/decisions.md`
- 部署：云服务器（已有，规格待确认；建议 2 核 4G）

## 验收结果

- 本机 `./mvnw verify`：130 个单元/slice 通过 + 13 个集成用例由 Failsafe 执行（无 Docker 跳过），Spotless、Checkstyle、JaCoCo 通过
- GitHub Actions（2026-11-19，run `44539442887`）：130 个单元/slice + 13 个集成（Testcontainers）通过；Flyway V1–V3、Spotless、Checkstyle、JaCoCo 全部通过
- 阶段 4 闭环：注册 → 注销 → 登录 40104 → RECOVER 发码/恢复 → 新密码登录；注销/恢复后旧令牌 401
- 阶段 4 边界：DELETE 码不能复用 RESET 码；管理员自助注销 403；封禁账号不能恢复
- 阶段 3 VM Docker（历史）：Flyway V1+V2；`AuthFlowIntegrationTest` 8/8、`AdminFlowIntegrationTest` 2/2

## 下一步（阶段 5 Web 前端）

1. `web/`：Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router；ESLint + Prettier
2. UI-01 注册/登录：邮箱验证码、60 秒倒计时、登录后保存令牌
3. UI-02 个人中心：资料、改密、忘记密码、注销（DELETE 码）、恢复（RECOVER 码）
4. UI-03 管理后台：分页/状态过滤、封禁/解封、重置密码（仅 ADMIN 路由守卫）
5. API 客户端：解析 `ProblemDetail.code`；401 清理令牌回登录；403 显示无权限；429 读 `Retry-After`
6. 开发联调：Vite `/api` 代理到 `http://localhost:8080`；后端 CORS 默认允许 `http://localhost:5173`

## 交接

- 新会话先读本文件与 `docs/stage-5-handoff.md`，再按需读 `docs/plan.md` §4.2、§7.3、§8、§11
- CI：`gh run list -R wuwwp/user-center -L 3`；`main` 应为绿灯；workflow 在 `.github/workflows/ci.yml`
- Git：阶段 4 已推送 `main`；工作区应以 `git status` 为准
- 课程文件在 `D:\teach\user-center-spring-security`（本地，不提交仓库）
- 关键约束：JWT 不含 role；改密/登出/封禁/恢复/角色变更/管理员重置必须自增 `token_version`
- 关键约束：注销只允许正常账号；恢复不恢复封禁状态；管理员审计与业务写入同事务

## 待确认 / 阻塞

- 前端令牌存放方式（内存 + 刷新丢失 vs `sessionStorage` vs `localStorage`）在阶段 5 首课决定
- 后续优化：Lua 原子化 `INCR + EXPIRE`、验证码 `GET + DEL`（见 `docs/decisions.md` §28）
- 云服务器规格未定；Nginx TLS 证书需部署时生成或申请
- 注销后的数据保留期限/匿名化未做，留到演进路线
