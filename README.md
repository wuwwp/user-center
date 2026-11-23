# user-center

[![CI](https://github.com/wuwwp/user-center/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/wuwwp/user-center/actions/workflows/ci.yml?query=branch%3Amain)

统一认证服务：邮箱注册/登录、JWT 鉴权、验证码找回、账号状态与登录防护。单体模块化，后续可演进为 OAuth2/SSO。

## 技术栈

- Java 21、Spring Boot 4.1、Spring Security 7
- MyBatis-Plus 3.5、MySQL 8.4、Redis 7
- Flyway 数据库迁移、JJWT、springdoc
- Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router
- JUnit 6、MockMvc、Testcontainers 2、JaCoCo、Vitest

## 已实现

- 注册、登录、登出、当前用户、修改密码、邮箱验证码重置
- JWT 无状态鉴权；改密/登出/封禁通过 `token_version` 即时吊销旧令牌
- 登录失败账号/IP 双维度锁定；发码邮箱/IP 限流；验证码失败次数上限
- Top 1000 弱密码字典（注册/重置/改密生效，登录不校验）
- RFC 9457 `ProblemDetail` 错误契约；405/415/406 等 MVC 异常统一映射
- Flyway 迁移；开发环境独立种子管理员，生产不创建默认账号
- 异步邮件投递；SMTP/log 双实现，生产禁止 log 模式
- 管理端：分页查询用户（邮箱掩码）、封禁/解封、随机密码重置；普通用户访问 403
- 账号注销与恢复：注销要求 DELETE 验证码，恢复要求 RECOVER 验证码和新密码；旧令牌立即失效
- 管理员操作审计：Flyway V3 `admin_audit_log`，封禁/解封/重置与审计同事务
- Web 前端：登录/注册/找回/恢复、个人中心、管理后台；`web/`

## 本地启动

```bash
# 1. 准备本地配置（含数据库、Redis、JWT 密钥，文件已 gitignore）
cp src/main/resources/application-local.example.yaml src/main/resources/application-local.yaml
# 编辑填入真实值

# 2. 启动 MySQL/Redis（开发覆盖文件会暴露端口）
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis

# 3. 启动应用；Flyway 自动建表，dev profile 会创建种子管理员
./mvnw spring-boot:run
```

- 默认地址：`http://localhost:8080`
- Swagger（dev/local）：`http://localhost:8080/swagger-ui.html`
- 种子管理员只在 `dev` profile 创建；生产只跑 `db/migration`，不会创建默认账号。

前端：

```bash
cd web
npm install
npm run dev
```

- 默认地址：`http://localhost:5173`
- `/api` 由 Vite 代理到 `http://localhost:8080`；生产由 Nginx 托管 `web/dist` 并反代 `/api`。

## 测试

```bash
./mvnw verify
```

- Surefire 跑单元/slice 测试；Failsafe 跑 `*IntegrationTest`，需要 Docker。
- 本机无 Docker 时集成测试跳过；VM/CI 会启动真实 MySQL 8.4 与 Redis 7。
- JaCoCo 行/分支门槛均为 80%。

## 部署

```bash
# 准备 deploy/nginx/certs/fullchain.pem 与 privkey.pem（说明见同目录 README）
cp .env.example .env   # 填写 DB/JWT/邮件等变量
docker compose up -d --build
```

- Nginx 镜像在前端构建阶段执行 `npm ci && npm run build`，部署不需要预先构建 `web/dist`。
- 2C2G 或镜像源较慢时，可本地构建后走预构建方式：把 `target/*.jar` 放到 `deploy/artifacts/app.jar`、`web/dist/` 放到 `deploy/artifacts/web/`，再执行 `docker compose -f docker-compose.yml -f docker-compose.prebuilt.yml up -d --build`。
- Nginx 只暴露 80/443，80 跳转 443；MySQL/Redis 不映射宿主机端口。
- 应用容器以非 root 运行，健康检查走 `/actuator/health`。
- 2C2G 默认参数：JVM `-Xmx384m`、MySQL buffer pool 256m、Redis maxmemory 128m；可用环境变量覆盖。
- 生产配置见 `application-prod.yaml`；敏感值全部通过环境变量注入。

## CI

GitHub Actions 工作流在 `.github/workflows/ci.yml`：每次 push 到 `main` 或开 PR 时并行运行后端 `./mvnw verify` 和前端 `web` job（`npm ci`、依赖审计、lint、Vitest、build）。

- 结果在仓库 **Actions** 页查看。
- 公开仓库免费；私有仓库每月有免费额度。
- Dependabot 配置（`.github/dependabot.yml`）覆盖 Maven、GitHub Actions 和 npm。
- 如 Actions 页没有运行记录，检查 **Settings → Actions → General → Actions permissions** 是否允许 Actions。

## 文档

- 文档地图：`docs/README.md`
- 进度与下一步：`docs/progress.md`
- 当前交接：`docs/stage-6-handoff.md`
- 设计：`docs/plan.md`；编码规范：`docs/standards.md`
- 历史阶段：`docs/archive/`

## 安全说明

- 不在仓库保存数据库密码、JWT 密钥、SMTP 授权码；只放本地 gitignore 配置或环境变量。
- 登录/发码/重置统一响应，避免用户枚举；注册冲突 409 是已接受的例外。
- 密码使用 BCrypt 存储；日志不记录密码、令牌、验证码和完整邮箱。
