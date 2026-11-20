# 阶段 5 交接：Web 前端（已完成）

> **状态：已完成（2026-11-20）。** 三个页面、认证流程、主题与安全基线已落地；本文保留为阶段 5 的设计与实现记录，下一阶段见 `docs/stage-6-handoff.md`。
> 更新日期：2026-11-20
> 设计见 `docs/plan.md` §4.2、§7.3、§8、§11。

## 1. 后端当前状态

- 认证：注册、登录、登出、改密、忘记密码、注销、恢复。
- 用户：`get /users/me`、`put /users/me/password`、`delete /users/me`、`post /users/me/recover`。
- 管理员：`get /admin/users`、`put /admin/users/{account}/status`、`post /admin/users/{account}/password/reset`。
- 成功响应直接返回 DTO，没有统一 envelope。
- 失败响应是 RFC 9457 `application/problem+json`，业务码在 `code` 字段；401/403 由过滤链直接写，401 带 `WWW-Authenticate: Bearer`，429 带 `Retry-After: 60`。
- JWT 有效期 2 小时，不放 role，没有 refresh token；角色每次从数据库读。

## 2. 页面范围（UI-01 ~ UI-03）

| 页面 | 内容 |
|---|---|
| UI-01 注册 / 登录 | 邮箱注册、验证码倒计时、登录；错误按 `code` 展示 |
| UI-02 个人中心 | 资料、改密、忘记密码、注销账号（DELETE 码）、恢复账号（RECOVER 码） |
| UI-03 管理后台 | 用户分页、状态过滤、封禁/解封、重置密码（随机密码只显示一次） |

管理后台只对 `ADMIN` 可见；前端路由守卫只是体验层，真正权限仍由后端 403 保证。

## 3. 技术栈

- Vue 3 + Vite + TypeScript
- Element Plus
- Pinia（认证状态）+ Vue Router（路由守卫）
- ESLint + Prettier

目录放 `web/`；构建产物由 Nginx 静态托管，`/api` 反向代理到应用。

## 4. 令牌存放（动手前先定）

三个选项：

1. **内存（Pinia）**：XSS 风险最小，但刷新页面就掉登录，需要重新登录。
2. **`sessionStorage`**：刷新保留、关闭标签页清除；仍有 XSS 读取风险。
3. **`localStorage`**：体验最好，但长期暴露在 XSS 下。

当前没有 refresh token，推荐先用 `sessionStorage`，并在 `docs/decisions.md` 记录理由；如果更看重安全演示，选内存 + 登录页回跳。

## 5. API 客户端约定

- 统一封装 `fetch`/`axios`，自动加 `Authorization: Bearer <token>`。
- 解析 `ProblemDetail`：优先展示 `detail`，同时保留 `code` 用于分支；不要匹配中文文案。
- `401`：清空令牌、跳登录页、记录原路径；不要循环重试。
- `403`：显示“无权限”，不要跳登录。
- `429`：读 `Retry-After`，禁用按钮倒计时。
- 注销接口是 `DELETE /api/v1/users/me`，请求体 `{"code":"..."}`；恢复接口是公开 `POST /api/v1/users/me/recover`。
- 管理员重置密码的响应里 `newPassword` 只返回一次，页面必须提示用户立即保存。

## 6. 联调与部署

开发：

- 后端默认允许 `http://localhost:5173`（`security.cors.allowed-origins`）。
- Vite 配 `/api` 代理到 `http://localhost:8080`，避免本地跨域和 Cookie 问题。
- 验证码在开发环境可能走 `MAIL_MODE=log`，从后端日志读取；前端不要显示验证码。

生产：

- `npm run build` 产物放 Nginx 静态目录。
- Nginx 将 `/api/` 反代到应用 8080；TLS 证书见 `deploy/nginx`。
- 前端只依赖相对路径 `/api/v1`。

## 7. 验收标准

- UI-01：注册（REGISTER 码）、登录、错误码展示、验证码 60 秒倒计时。
- UI-02：查看资料、改密后旧令牌失效并回登录、忘记密码、注销（DELETE 码）、恢复（RECOVER 码）。
- UI-03：管理员分页/状态过滤、封禁/解封、重置密码并提示一次性密码；普通用户看不到入口，直接访问返回 403 提示。
- 所有 401/403/429 按 `code` 处理，不依赖后端文案。
- `npm run lint`、`npm run build` 通过；如加测试，至少覆盖 API 客户端和路由守卫。

## 8. 开放决策

1. 令牌存内存、`sessionStorage` 还是 `localStorage`？**已定：`sessionStorage`。**
2. 是否引入 Axios，还是直接用 `fetch` 封装？**已定：Axios。**
3. 管理后台重置密码后，是否要求用户下一次登录强制改密？（后端本期不做）
4. 前端是否做 Vitest/Playwright，还是只做 lint + build？**已定：Vitest 覆盖 API 客户端和 auth store，不做 E2E。**
5. 前端 CI 与 Dependabot npm：已准备配置；`web` job 需要 `workflow` scope 才能推送 workflow 文件。

## 9. 实现结果

- 目录：`web/`；Vue 3 + Vite + TypeScript strict + Element Plus + Pinia + Vue Router + Axios。
- 路由：`/login`（登录/注册/忘记密码/恢复账号）、`/profile`、`/admin`；角色跳转与管理员守卫。
- 认证：`sessionStorage` 令牌；刷新调用 `GET /users/me`；401 清令牌回登录；403/429 统一提示。
- UI：墨绿/暖白/炭黑主题；登录页双栏卡片；管理端左侧导航 + 表格 + 重置密码一次性弹窗。
- 安全：禁用 `v-html`；站内 `returnUrl`；生产构建关闭 source map；Nginx 增加 CSP/HSTS/安全头。
- 质量：`npm run lint`、5 个 Vitest、`npm run build` 通过；主包约 871 kB（gzip 278 kB），后续可按需引入 Element Plus 优化。

## 10. 阶段 6 待办

见 `docs/stage-6-handoff.md`：CI `web` job、OpenAPI 导出、Docker/Compose 构建前端、k6 压测、部署与 TLS。
