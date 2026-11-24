# 项目进度

更新日期：2026-11-24
设计文档版本：v0.21
当前阶段：阶段 6 已部署到云服务器（IP 自签 TLS）；待域名证书

## 项目定位（护栏）

- 第一方统一认证服务；单次部署、内部模块化单体，不拆微服务
- 只做身份：注册、登录、登出、改密、验证码、注销/恢复、封禁、平台管理员
- 业务角色/团队/空间/项目权限由各业务项目自己维护
- 非目标：OAuth2/OIDC、RS256、refresh token、微服务拆分、真正跨应用 SSO
- 令牌含 `sub/ver/aud/jti/iat/exp/iss`，2 小时，不放 role；角色以数据库为准
- Redis 只用于验证码、发码限频、登录失败锁定

## 已完成

- 阶段 0/1：工程骨架、统一异常、JWT 鉴权链、注册/登录/登出/改密/验证码重置
- 阶段 2：账号/IP 锁定、发码限流、验证码失败作废、弱密码字典、可信 `remoteAddr`
- 生产化：ProblemDetail、JWT HS256+aud、Flyway、异步邮件、Failsafe、TLS/CI、依赖扫描
- 阶段 3：管理端分页、封禁/解封、随机密码重置；管理端 403
- 阶段 4：DELETE 验证码注销、RECOVER 验证码恢复、Flyway V3 管理员审计
- 阶段 5：`web/` 三页（登录/注册/找回/恢复、个人中心、管理后台）；sessionStorage + Axios + Element Plus
- 阶段 6：OpenAPI 快照、Nginx 镜像内构建前端、预构建部署、k6 登录压测、云服务器上线与安全头验收

## 测试与验收

- 后端：132 个单元/slice + 13 个集成；覆盖门槛 80% 通过
- 前端：6 个 Vitest；`npm run lint`、`npm run build` 通过
- CI：后端 `verify` + 前端 `web` job（Node 24、audit、lint、Vitest、build）
- 生产验收：HTTPS 登录、`/users/me`、`/admin/users` 均 200；CSP/HSTS 等安全头存在
- 压测：成功登录 52 次 0 失败，P95 174ms；第 6 次失败触发 40102
- 容量压测（10 万数据）：登录/注册/重置约 20 rps，改密 5–8 rps；瓶颈是 app CPU + Hikari 10 连接

## 环境

- 本机：Windows 11、JDK 21（`D:\dev\jdk-21`）、Node 24；本机无 Docker
- 开发虚拟机 `ssh user-center-vm`（192.168.216.130）：Docker 29.9.2
- 云服务器：阿里云 2 核 2G、Ubuntu 24.04、Docker 29、2G swap；代码在 `/opt/user-center`
- 生产部署：`docker compose -f docker-compose.yml -f docker-compose.prebuilt.yml up -d --build`
- MySQL 8.4、Redis 7.4；开发启动 `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis`
- 种子管理员仅 dev profile 创建；生产管理员通过 SQL 引导创建，凭据不入仓库

## 下一步

1. 域名 A 记录指向服务器，换 Let's Encrypt 证书，去掉浏览器自签警告
2. 可选：按容量压测结论调大 Hikari 池、评估 BCrypt 并发策略
3. 可选：Element Plus 按需引入，降低主包体积
4. 可选：Lua 原子化 `INCR + EXPIRE`、验证码 `GET + DEL`
5. 可选：注销数据保留/匿名化策略

## 交接

- 当前交接：`docs/stage-6-handoff.md`；历史阶段见 `docs/archive/`
- 设计：`docs/plan.md`；规范：`docs/standards.md`；取舍：本地 `docs/decisions.md`
- 后端 CI：`gh run list -R wuwwp/user-center -L 3`
- 前端本地：`cd web && npm ci && npm run lint && npm run test:unit && npm run build`
- 关键约束：改密/登出/封禁/恢复/角色变更/管理员重置必须自增 `token_version`
- 关键约束：注销只允许正常账号；恢复不恢复封禁状态；管理员审计与业务写入同事务
- main 为压测脚本提交（55139d2），无应用代码变更；云服务器仍为 7463017，无需重新部署

## 待确认 / 阻塞

- 域名未定：当前用 IP 自签证书，浏览器会提示不受信任；提供域名后可换受信任证书
- 后续优化：Element Plus 按需引入、Lua 原子化、注销数据保留/匿名化
