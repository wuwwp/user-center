# 项目进度

更新日期：2026-11-13
设计文档版本：v0.4
当前阶段：阶段 0（工程基础）已完成

## 已完成

- `docs/plan.md` 定稿到 v0.4；决策记录在 `docs/decisions.md`（本地，不提交）
- Git 仓库：https://github.com/wuwwp/user-center （main）
- JDK 21（`D:\dev\jdk-21`，`JAVA_HOME` 已指向；Git Bash 里裸 `java` 仍是 17，运行应用请用 `"$JAVA_HOME/bin/java"` 或 `./mvnw`）
- 开发虚拟机 `ssh user-center-vm`（192.168.216.130）SSH 免密、Docker 29.9.2 可用
- 阶段 0 工程骨架：
  - 依赖：webmvc、security、validation、data-redis、mail、actuator、MyBatis-Plus 3.5.18（含 jsqlparser 分页模块）、jjwt 0.13.0、springdoc 3.2.0、Testcontainers 2.0.5
  - 配置分层：`application.yaml` / `-dev` / `-local.example` / `-local`（gitignore）/ `-prod`
  - `common`：`BaseResponse`、`ErrorCode`、`BusinessException`、`GlobalExceptionHandler`
  - `security`：`JwtTokenProvider`（HS256、issuer、ver/role/jti claims）、`JwtAuthenticationFilter`（每请求查用户行比对版本/状态/角色）、401/403 JSON 处理器、`SecurityUtils`
  - `SecurityConfig`：无状态、CORS 白名单、管理路径 `ROLE_ADMIN`、安全响应头
  - `model`：`User` 实体、`UserRole`/`UserStatus`/`DeletedFlag` 枚举、登录入参与 DTO
  - 最小闭环：`POST /api/v1/auth/login`、`GET /api/v1/users/me`（阶段 1 在此基础上扩展）
  - `src/main/resources/db/init.sql`：建库建表 + 种子管理员（`set names utf8mb4` 防止中文双重编码）
  - `Dockerfile`、`docker-compose.yml`、`deploy/nginx/default.conf`、`deploy/maven/settings.xml`、`.env.example`
  - 质量工具：Spotless（google-java-format）、Checkstyle 12.3.1（`config/checkstyle/`）、`.editorconfig`
- 测试：`JwtTokenProviderTest`、`SecurityConfigTest`（401/403/参数校验/登录成功），共 9 个用例通过

## 环境

- 本机：Windows 11、JDK 21、Node 24；本机无 Docker
- 开发虚拟机：Docker Compose 项目 `~/user-center`
  - MySQL 8.4.13：宿主 `3306`，库 `usercenter`，账号 `usercenter`（密码在 VM `.env` 与 `application-local.yaml`）
  - Redis 7.4：宿主 `6380` → 容器 `6379`（VM 的 6379 已被其他服务占用）
  - 容器：`user-center-mysql-1`、`user-center-redis-1`
  - 启动/停止：`ssh user-center-vm "cd ~/user-center && docker compose up -d mysql redis"`
- 种子管理员：`admin0001` / `admin@user-center.local`，密码见本地 `docs/decisions.md`（仅开发环境，部署前必须修改）
- 部署：云服务器（已有，规格待确认；建议 2 核 4G）
- 数据库统一 MySQL 8.4 LTS

## 验收结果（阶段 0）

- `./mvnw verify`：编译、9 个测试、Spotless、Checkstyle 全部通过
- 应用启动成功（Java 21、端口 8080、虚拟线程开启）
- 未登录 `GET /api/v1/users/me` → 401 `{"code":40100}`
- 种子管理员登录 → 200 + JWT；携带令牌 `GET /api/v1/users/me` → 200 + 用户资料
- 错误密码 → 40101；非法令牌 → 401；参数校验失败 → 40000；受保护资源不存在 → 40400
- `/v3/api-docs`、`/swagger-ui.html` 可访问；`/actuator/env` 被拦截
- `docker compose config -q` 通过；应用镜像尚未实际构建

## 下一步

1. 阶段 1：注册、登出、当前用户完善、修改密码、邮箱验证码重置
   - 验证码 Redis 场景隔离与限发、注册冲突、密码策略（阶段 2 深化限流/锁定）
   - 登录失败计数、账号/IP 限流放到阶段 2
2. 阶段 1 完成后补 Testcontainers 集成测试（本机无 Docker，可先写用例，在 VM 或 CI 执行）

## 待确认 / 阻塞

- SMTP 授权码未提供，阶段 1 邮件先用日志输出，不阻塞
- 云服务器规格未定，部署阶段前确认
- 应用镜像与 Nginx/前端静态资源尚未联调（阶段 5/6）

## 备注

- 敏感配置只放 `application-local.yaml`（已 gitignore）或 VM `.env`，不得提交
- 本机 Git Bash 的 `java` 指向 17；Maven 使用 `JAVA_HOME` 的 21，直接用 `java -jar` 时需指定完整路径
