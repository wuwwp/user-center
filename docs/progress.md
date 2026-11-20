# 项目进度

更新日期：2026-11-20
设计文档版本：v0.21
当前阶段：阶段 5 Web 前端完成；阶段 6 收尾与部署待开始

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

## 测试与验收

- 后端：130 个单元/slice + 13 个集成；覆盖门槛 80% 通过
- 前端：5 个 Vitest；`npm run lint`、`npm run build` 通过
- CI：后端 `verify` + 前端 `web` job（Node 24、audit、lint、Vitest、build）
- 阶段 4 CI run `44539442887` 全绿；阶段 5 前端页面本地 Vite + mock 验证

## 环境

- 本机：Windows 11、JDK 21（`D:\dev\jdk-21`）、Node 24；本机无 Docker
- 开发虚拟机 `ssh user-center-vm`（192.168.216.130）：Docker 29.9.2
- MySQL 8.4、Redis 7.4；开发启动 `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis`
- 种子管理员仅 dev profile 创建；云服务器规格待确认（建议 2 核 4G）

## 下一步（阶段 6）

1. OpenAPI 导出，补全接口示例
2. Docker/Compose 构建前端产物，Nginx 静态托管与安全头验证
3. k6 登录压测脚本与报告
4. 云服务器部署、TLS 证书、验收
5. 可选：Element Plus 按需引入，降低主包体积

## 交接

- 当前交接：`docs/stage-6-handoff.md`；历史阶段见 `docs/archive/`
- 设计：`docs/plan.md`；规范：`docs/standards.md`；取舍：本地 `docs/decisions.md`
- 后端 CI：`gh run list -R wuwwp/user-center -L 3`
- 前端本地：`cd web && npm ci && npm run lint && npm run test:unit && npm run build`
- 关键约束：改密/登出/封禁/恢复/角色变更/管理员重置必须自增 `token_version`
- 关键约束：注销只允许正常账号；恢复不恢复封禁状态；管理员审计与业务写入同事务

## 待确认 / 阻塞

- 云服务器规格未定；Nginx TLS 证书需部署时生成或申请
- 后续优化：Lua 原子化 `INCR + EXPIRE`、验证码 `GET + DEL`
- 注销后的数据保留期限/匿名化未做，留到演进路线
