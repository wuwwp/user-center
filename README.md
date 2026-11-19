# user-center

统一认证服务：邮箱注册/登录、JWT 鉴权、验证码找回、账号状态与登录防护。单体模块化，后续可演进为 OAuth2/SSO。

## 技术栈

- Java 21、Spring Boot 4.1、Spring Security 7
- MyBatis-Plus 3.5、MySQL 8.4、Redis 7
- Flyway 数据库迁移、JJWT、springdoc
- JUnit 6、MockMvc、Testcontainers 2、JaCoCo

## 已实现

- 注册、登录、登出、当前用户、修改密码、邮箱验证码重置
- JWT 无状态鉴权；改密/登出/封禁通过 `token_version` 即时吊销旧令牌
- 登录失败账号/IP 双维度锁定；发码邮箱/IP 限流；验证码失败次数上限
- Top 1000 弱密码字典（注册/重置/改密生效，登录不校验）
- RFC 9457 `ProblemDetail` 错误契约；405/415/406 等 MVC 异常统一映射
- Flyway 迁移；开发环境独立种子管理员，生产不创建默认账号
- 异步邮件投递；SMTP/log 双实现，生产禁止 log 模式

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

- Nginx 只暴露 80/443，80 跳转 443；MySQL/Redis 不映射宿主机端口。
- 应用容器以非 root 运行，健康检查走 `/actuator/health`。
- 生产配置见 `application-prod.yaml`；敏感值全部通过环境变量注入。

## CI

GitHub Actions 工作流在 `.github/workflows/ci.yml`：每次 push 到 `main` 或开 PR 时运行 `./mvnw verify`，包括格式检查、单元/slice 测试、Testcontainers 集成测试和覆盖率门槛。

- 结果在仓库 **Actions** 页查看。
- 公开仓库免费；私有仓库每月有免费额度。
- Dependabot 配置（`.github/dependabot.yml`）已启用。
- 如 Actions 页没有运行记录，检查 **Settings → Actions → General → Actions permissions** 是否允许 Actions。

## 文档

- 设计：`docs/plan.md`
- 进度与下一步：`docs/progress.md`
- 编码规范：`docs/standards.md`
- 阶段交接：`docs/stage-3-handoff.md`

## 安全说明

- 不在仓库保存数据库密码、JWT 密钥、SMTP 授权码；只放本地 gitignore 配置或环境变量。
- 登录/发码/重置统一响应，避免用户枚举；注册冲突 409 是已接受的例外。
- 密码使用 BCrypt 存储；日志不记录密码、令牌、验证码和完整邮箱。
