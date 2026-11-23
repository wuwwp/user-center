# 阶段 6 交接：收尾与部署

> **状态：部署完成，仅域名证书待补。** 设计见 `docs/plan.md` §9、§10、§11。
> 更新日期：2026-11-23
> 先读 `docs/progress.md`，再读本文。

## 1. 当前状态

- 后端：注册/登录/登出/改密/验证码/注销/恢复/管理员/审计全部完成。
- 前端：`web/` 三个页面完成；`npm run lint`、5 个 Vitest、`npm run build` 通过。
- 数据库：Flyway V1–V3 在 CI/Testcontainers 通过。
- 后端 CI：run `44539442887` 全绿。
- 前端 CI：`web` job 已加入 `.github/workflows/ci.yml`，Dependabot 已覆盖 npm（`/web`）。

## 2. 待办

### 2.1 前端 CI 与 Dependabot（已完成）

- `.github/workflows/ci.yml` 的 `web` job：Node 24、`npm ci`、`npm audit --omit=dev --audit-level=high`、`npm run lint`、`npm run test:unit`、`npm run build`，与后端 `verify` 并行。
- `.github/dependabot.yml` 已增加 npm ecosystem（`/web`）。

### 2.2 OpenAPI 与文档（已完成）

- 已导出 `docs/api/openapi.json`（OpenAPI 3.1，11 个路径）；生产关闭 springdoc，接口变更后重新导出。
- README 已补预构建部署方式与 TLS 证书说明。

### 2.3 Docker / Compose（已完成）

- `deploy/nginx/Dockerfile` 在 Node 阶段构建前端，Nginx 镜像直接托管 `dist`；`docker compose up -d --build` 可从空环境启动。
- 2C2G 或镜像源慢时使用预构建方式：`docker compose -f docker-compose.yml -f docker-compose.prebuilt.yml up -d --build`，产物放 `deploy/artifacts/`（已 gitignore）。
- 生产 Nginx 已加 CSP/HSTS/X-Frame-Options/Referrer-Policy；上线后验证通过。

### 2.4 压测（已完成）

- `perf/login.js`：成功登录、错误密码、锁定触发三条场景。
- 报告 `docs/perf/README.md` 与原始输出 `docs/perf/login-2026-11-23.txt`。
- 成功场景 52 次 0 失败，P95 174ms；第 6 次失败返回 40102。

### 2.5 部署与验收（已完成，域名证书待补）

- 阿里云 2 核 2G，Ubuntu 24.04，2G swap；旧容器已停止并取消自启。
- 启动顺序由 compose 健康检查保证：MySQL/Redis → app → Nginx。
- HTTPS 验收通过：登录、`/users/me`、`/admin/users` 均 200；安全头齐全；CSP 下页面正常渲染。
- 当前使用 IP 自签证书；有域名后按 `deploy/nginx/README.md` 换 Let's Encrypt。

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
