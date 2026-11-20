# 阶段 6 交接：收尾与部署

> **状态：待开始。** 阶段 5 已完成；设计见 `docs/plan.md` §9、§10、§11。
> 更新日期：2026-11-20
> 先读 `docs/progress.md`，再读本文。

## 1. 当前状态

- 后端：注册/登录/登出/改密/验证码/注销/恢复/管理员/审计全部完成。
- 前端：`web/` 三个页面完成；`npm run lint`、5 个 Vitest、`npm run build` 通过。
- 数据库：Flyway V1–V3 在 CI/Testcontainers 通过。
- 后端 CI：run `44539442887` 全绿。
- 前端 CI：`web` job 已写入本地 `.github/workflows/ci.yml`，但当前 gh token 没有 `workflow` scope，尚未推送。

## 2. 待办

### 2.1 前端 CI 与 Dependabot

- 推送 `.github/workflows/ci.yml` 的 `web` job：
  - Node 24、`npm ci`、`npm audit --omit=dev --audit-level=high`、`npm run lint`、`npm run test:unit`、`npm run build`
  - 与后端 `verify` 并行
- `.github/dependabot.yml` 已增加 npm ecosystem（`/web`）。
- 推送 workflow 需要网页编辑，或 `gh auth refresh -s workflow` 后再推。

### 2.2 OpenAPI 与文档

- 导出 OpenAPI JSON/YAML，放入 `docs/api/` 或构建产物；确认注销/恢复/管理员接口的请求体与错误码完整。
- README 已补充前端启动与 CI；阶段 6 再补部署命令和截图。

### 2.3 Docker / Compose

- 当前 `docker-compose.yml` 的 Nginx 挂载 `./web/dist`，但没有构建前端。
- 二选一：
  1. 增加 `web` 构建阶段：Node 构建后把 `dist` 复制到 Nginx 镜像；
  2. 在部署脚本中先 `cd web && npm ci && npm run build`，再 `docker compose up -d --build`。
- 推荐方案 1，部署只需一条 compose 命令；保持后端 Dockerfile 多阶段构建。
- 生产 Nginx 已加 CSP/HSTS/X-Frame-Options/Referrer-Policy；上服务器后验证响应头。

### 2.4 压测

- 新增 `perf/login.js`（k6）：登录成功、错误密码、锁定触发三条场景。
- 输出报告到 `docs/perf/`；不设硬性性能门槛，记录环境、并发、P95/P99。

### 2.5 部署与验收

- 云服务器规格确认（建议 2 核 4G）。
- TLS 证书（Let's Encrypt / 云厂商 DV / 自签用于内网）。
- 启动顺序：MySQL/Redis → 后端健康 → Nginx。
- 验收：注册 → 登录 → 改密 → 注销 → 恢复；管理员封禁/解封/重置；CSP 不阻断页面。

### 2.6 可选优化

- Element Plus 按需引入，降低当前约 871 kB 主包。
- 前端 E2E（Playwright）或组件测试。
- Redis Lua 原子化、验证码 `GET + DEL`。
- 注销数据保留/匿名化策略。

## 3. 命令

```bash
# 后端
./mvnw verify

# 前端
cd web
npm ci
npm run lint
npm run test:unit
npm run build

# CI
gh run list -R wuwwp/user-center -L 3
```

## 4. 完成定义

- 后端 + 前端 CI 均绿。
- `docker compose up -d --build` 可从空环境启动，Nginx 提供前端与 `/api`。
- OpenAPI 可导出；README 部署步骤可复现。
- k6 报告可复现。
- 生产 HTTPS、CSP、安全头验证通过。
