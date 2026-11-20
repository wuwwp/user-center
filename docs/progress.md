# 项目进度

更新日期：2026-11-20
设计文档版本：v0.21
当前阶段：阶段 5 Web 前端完成（本机）；阶段 6 收尾待开始

## 项目定位（护栏）

- 定位：第一方统一认证服务；单次部署、内部模块化单体，不拆微服务
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
- 阶段 4 注销与恢复（2026-11-19）：DELETE 验证码注销、RECOVER 验证码恢复、管理员审计表 V3；封禁账号不可恢复
- 阶段 5 Web 前端（2026-11-20）：
  - `web/`：Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + Axios
  - `/login`：登录、注册、忘记密码、恢复账号；验证码 60 秒倒计时
  - `/profile`：资料、改密、注销（DELETE 码）；`/admin`：分页、状态过滤、封禁/解封、重置密码
  - 令牌存 `sessionStorage`，刷新用 `GET /users/me` 恢复；401 清令牌回登录，403/429 统一处理
  - 墨绿 + 暖白主题；桌面优先，768px 以上可用；管理端左侧导航
  - 安全基线：禁用 `v-html`、CSP、站内 `returnUrl`、生产关闭 source map、锁定依赖版本
- 测试：后端 130 个单元/slice + 13 个集成；前端 5 个 Vitest 用例

## 环境

- 本机：Windows 11、JDK 21（`D:\dev\jdk-21`）、Node 24；本机无 Docker
- 开发虚拟机 `ssh user-center-vm`（192.168.216.130）：Docker 29.9.2
  - MySQL 8.4.13、Redis 7.4；开发启动：`docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis`
  - 生产只启动 `docker compose up -d`，Nginx 使用 `deploy/nginx/certs` 下的证书
- 种子管理员：`admin0001` / `admin@user-center.local`，仅 dev profile 创建，密码见本地 `docs/decisions.md`
- 部署：云服务器（已有，规格待确认；建议 2 核 4G）

## 验收结果

- 后端本机 `./mvnw verify`：130 个单元/slice 通过 + 13 个集成用例（无 Docker 跳过）
- 后端 CI run `44539442887`：130 + 13 通过；Flyway V1–V3、Spotless、Checkstyle、JaCoCo 通过
- 前端：`npm run lint`、`npm run test:unit`（5/5）、`npm run build` 通过
- 前端页面：登录/注册/找回/恢复、个人中心、管理后台已在本地 Vite 联调验证；管理端重置密码弹窗只显示一次

## 下一步（阶段 6 收尾）

1. 前端 CI `web` job（Node 24、`npm ci`、audit、lint、Vitest、build）与后端 `verify` 并行
2. OpenAPI 导出；完善 README 与部署说明
3. Dockerfile / docker-compose 构建前端产物；Nginx 静态托管与安全头
4. k6 登录压测脚本与报告
5. 云服务器部署、TLS 证书、验收
6. 可选：Element Plus 按需引入，降低 870 kB 主包体积

## 交接

- 新会话先读本文件与 `docs/stage-6-handoff.md`，再按需读 `docs/plan.md` §4.2、§7.3、§8、§11
- 后端 CI：`gh run list -R wuwwp/user-center -L 3`；前端本地：`cd web && npm run lint && npm run test:unit && npm run build`
- Git：阶段 5 代码在 `main`；工作区应以 `git status` 为准
- 关键约束：JWT 不含 role；改密/登出/封禁/恢复/角色变更/管理员重置必须自增 `token_version`
- 关键约束：注销只允许正常账号；恢复不恢复封禁状态；管理员审计与业务写入同事务
- 前端约束：错误按 `ProblemDetail.code` 处理；令牌不进 URL/日志；不使用 `v-html`

## 待确认 / 阻塞

- 前端 CI `web` job 需要修改 `.github/workflows/ci.yml`；当前 gh token 没有 `workflow` scope，需网页编辑或 `gh auth refresh -s workflow`
- 云服务器规格未定；Nginx TLS 证书需部署时生成或申请
- 后续优化：Lua 原子化 `INCR + EXPIRE`、验证码 `GET + DEL`（见 `docs/decisions.md` §28）
- 注销后的数据保留期限/匿名化未做，留到演进路线
