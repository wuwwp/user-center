# 项目进度

更新日期：2026-11-15
设计文档版本：v0.6
当前阶段：阶段 0 完成（含 JWT 去 role、登录事务边界修订）；下一步阶段 1

## 项目定位（护栏）

- 定位：简历项目 + 第一方统一认证服务；单次部署、内部模块化单体，不拆微服务
- 只做身份：注册、登录、登出、改密、验证码、注销/恢复、封禁、平台管理员
- 业务角色/团队/空间/项目权限由各业务项目自己维护，user-center 不提供
- 非目标（本期）：OAuth2/OIDC、RS256、refresh token、内部微服务拆分、真正跨应用 SSO
- 令牌：JWT 仅含 `sub/ver/jti/iat/exp/iss`，2 小时，不放 role；角色以数据库为准
- 接入其它项目：先做完阶段 1–6，再选一个项目接入；introspection 到那时再实现
- Redis：只用于验证码、发码限频、登录失败锁定；不缓存 introspection

## 已完成

- 阶段 0 工程骨架：依赖、配置分层、`common`/`security`/`config`/`model`、登录与当前用户最小闭环
- `JwtTokenProvider`：HS256、issuer、`sub/ver/jti/iat/exp/iss`，不含 role；`JwtAuthenticationFilter` 每请求查用户行，比对版本/状态，角色从数据库读取
- `SecurityConfig`：无状态、CORS 白名单、管理路径 `ROLE_ADMIN`、401/403 统一 JSON、安全响应头
- `init.sql`：建库建表 + 种子管理员（`set names utf8mb4` 防止中文双重编码）
- `Dockerfile`、`docker-compose.yml`、`deploy/nginx/default.conf`、`deploy/maven/settings.xml`、`.env.example`
- 质量工具：Spotless（google-java-format）、Checkstyle 12.3.1、`.editorconfig`
- 测试：`JwtTokenProviderTest`（含“令牌不含 role”回归断言）、`SecurityConfigTest`，共 10 个用例通过
- 计划 v0.6：阶段 1 登录密码校验放在事务外，短事务只包 `last_login_time` 更新
- Git 仓库：https://github.com/wuwwp/user-center （main）

## 环境

- 本机：Windows 11、JDK 21（`D:\dev\jdk-21`）、Node 24；本机无 Docker
- Git Bash 里裸 `java` 仍是 17；运行应用用 `"$JAVA_HOME/bin/java" -jar target/*.jar` 或 `./mvnw`
- 开发虚拟机 `ssh user-center-vm`（192.168.216.130）：Docker 29.9.2
  - MySQL 8.4.13：宿主 `3306`，库 `usercenter`，账号 `usercenter`（密码在 VM `.env` 与 `application-local.yaml`）
  - Redis 7.4：宿主 `6380` → 容器 `6379`（VM 的 6379 被其他服务占用）
  - 容器：`user-center-mysql-1`、`user-center-redis-1`；启动：`ssh user-center-vm "cd ~/user-center && docker compose up -d mysql redis"`
- 种子管理员：`admin0001` / `admin@user-center.local`，密码见本地 `docs/decisions.md`（仅开发，部署前必须改）
- 部署：云服务器（已有，规格待确认；建议 2 核 4G）

## 验收结果（阶段 0）

- `./mvnw verify`：编译、10 个测试、Spotless、Checkstyle 全部通过
- 应用启动成功（Java 21、端口 8080、虚拟线程开启）
- 未登录 `GET /api/v1/users/me` → 401 `{"code":40100}`
- 种子管理员登录 → 200 + JWT；携带令牌 `GET /api/v1/users/me` → 200 + 用户资料
- 错误密码 → 40101；非法令牌 → 401；参数校验失败 → 40000；受保护资源不存在 → 40400
- `/v3/api-docs`、`/swagger-ui.html` 可访问；`/actuator/env` 被拦截
- `docker compose config -q` 通过；应用镜像尚未实际构建

## 下一步（阶段 1）

1. 注册、登出、当前用户、修改密码、邮箱验证码重置
   - 登出、改密必须自增 `token_version`；角色变更（运维 SQL）也必须自增
   - 登录事务边界：BCrypt 校验在事务外，短事务只包 `last_login_time` 更新
   - 验证码 Redis 场景隔离与限发、注册冲突、密码策略；限流/锁定放阶段 2
2. 阶段 1 完成后补 Testcontainers 集成测试（本机无 Docker，可在 VM 或 CI 执行）
3. 三个业务项目接入、introspection、真 SSO：均不在阶段 1–6，写在演进路线

## 交接

- 新会话先读本文件，再按需读 `docs/plan.md` §8.2 与 §11；设计变更先改文档再改代码
- 课程文件在 `D:\teach\user-center-spring-security`（本地，不提交仓库）；项目代码在 `D:\yupiProject\user-center`
- 应用当前未运行；VM 上 MySQL/Redis 容器保持运行，启动命令见上方
- 关键约束：JWT 不含 role；角色以数据库为准；改密/登出/封禁/恢复/角色变更必须自增 `token_version`；introspection 延后到接入第一个项目
- 敏感配置只放 `application-local.yaml`（gitignore）或 VM `.env`

## 待确认 / 阻塞

- SMTP 授权码未提供，阶段 1 邮件先用日志输出，不阻塞
- 云服务器规格未定，部署阶段前确认
- 应用镜像与 Nginx/前端静态资源尚未联调（阶段 5/6）
