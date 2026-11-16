# 项目进度

更新日期：2026-11-16
设计文档版本：v0.9
当前阶段：阶段 1 完成（账号核心）；下一步阶段 2

## 项目定位（护栏）

- 定位：简历项目 + 第一方统一认证服务；单次部署、内部模块化单体，不拆微服务
- 只做身份：注册、登录、登出、改密、验证码、注销/恢复、封禁、平台管理员
- 业务角色/团队/空间/项目权限由各业务项目自己维护，user-center 不提供
- 非目标（本期）：OAuth2/OIDC、RS256、refresh token、内部微服务拆分、真正跨应用 SSO
- 令牌：JWT 仅含 `sub/ver/jti/iat/exp/iss`，2 小时，不放 role；角色以数据库为准
- 接入其它项目：先做完阶段 1–6，再选一个项目接入；introspection 到那时再实现
- Redis：只用于验证码、发码限频、登录失败锁定；不缓存 introspection

## 已完成

- 阶段 0：工程骨架、配置分层、统一返回/异常、JWT 鉴权链、`init.sql`、Docker 编排骨架、质量工具
- 阶段 1 接口：注册、登录、登出、当前用户、修改密码、验证码重置密码
  - 注册：验证码校验、自动 `user_account`（`u` + 8 位随机）、固定 `role=USER`、邮箱冲突 40900
  - 密码策略：8~64 位且含字母与数字（`ValidationConstants.PASSWORD_REGEXP`），失败 40000
  - 验证码：`verify:{scene}:{email}` 场景隔离，6 位数字、5 分钟、一次性；同邮箱 60 秒冷却、每日 10 次（42900）
  - 发码接口统一返回成功；仅向该场景真正需要的账号投递，避免用户枚举
  - 登录：BCrypt 校验在事务外，`UserCredentialService.recordLogin` 短事务只更新 `last_login_time`
  - 登出、改密、验证码重置均自增 `token_version`，旧令牌立即 401
  - 未接 SMTP：`LoggingEmailSender` 把验证码写入日志（邮箱掩码）；生产 `log-code=false`，验证码无法投递（已知阻塞）
  - 时区：每日限发按 `user-center.time-zone`（默认 `Asia/Shanghai`）计算，`Clock` 注入；容器 `TZ` 固定，避免 UTC 容器在 08:00 跨日
- 测试：43 个用例通过（JWT 5、Web/安全 10、Auth 15、User 5、验证码 8）
- 计划 v0.9：重置接口、验证码规则、时区治理；演进路线记录 passwordless 候选

## 环境

- 本机：Windows 11、JDK 21（`D:\dev\jdk-21`）、Node 24；本机无 Docker
- Git Bash 里裸 `java` 仍是 17；运行应用用 `"$JAVA_HOME/bin/java" -jar target/*.jar` 或 `./mvnw`
- 开发虚拟机 `ssh user-center-vm`（192.168.216.130）：Docker 29.9.2
  - MySQL 8.4.13：宿主 `3306`，库 `usercenter`，账号 `usercenter`（密码在 VM `.env` 与 `application-local.yaml`）
  - Redis 7.4：宿主 `6380` → 容器 `6379`（VM 的 6379 被其他服务占用）
  - 容器：`user-center-mysql-1`、`user-center-redis-1`；启动：`ssh user-center-vm "cd ~/user-center && docker compose up -d mysql redis"`
- 种子管理员：`admin0001` / `admin@user-center.local`，密码见本地 `docs/decisions.md`（仅开发，部署前必须改）
- 部署：云服务器（已有，规格待确认；建议 2 核 4G）

## 验收结果（阶段 1）

- `./mvnw verify`：43 个测试、Spotless、Checkstyle 全部通过
- 注册：发码 → 注册 200 + `userAccount`；重复注册 40900；弱密码 40000
- 登录：200 + JWT；改密后旧令牌 `/users/me` → 401，新密码可登录；错误旧密码 40101
- 重置：错误验证码 40001；正确验证码重置成功；重置后旧令牌失效
- 登出：200，随后同一令牌 `/users/me` → 401；未认证登出 → 401
- 验证码：60 秒内重复发送 42900；register 场景验证码用于 reset → 40002；未注册邮箱发 reset 码返回 200 且不投递
- 时区：UTC 16:30（北京次日 00:30）生成的每日计数键为次日日期，跨日边界正确
- 数据库核对：`role=0`、`is_delete=0`、`token_version=3`（改密/重置/登出各 +1）、BCrypt 前缀 `$2a$10$`
- 阶段 0 行为保持：未登录 401、非法令牌 401、参数校验 40000、受保护资源 40400

## 下一步（阶段 2）

1. 登录失败 5 次锁定 15 分钟（账号 + IP 双维度）；发码/登录限流
2. 弱密码 Top 1000 字典校验；验证码失败次数限制
3. 客户端 IP 只信任 Nginx 追加的 `X-Forwarded-For` 最后一跳
4. 安全用例补齐：令牌失效矩阵、越权、用户枚举响应一致性
5. 阶段 1 补 Testcontainers 集成测试（本机无 Docker，在 VM 或 CI 执行）

## 交接

- 新会话先读本文件，再按需读 `docs/plan.md` §8.2 与 §11；设计变更先改文档再改代码
- 课程文件在 `D:\teach\user-center-spring-security`（本地，不提交仓库）；项目代码在 `D:\yupiProject\user-center`
- 应用当前未运行；VM 上 MySQL/Redis 容器保持运行，启动命令见上方
- 关键约束：JWT 不含 role；角色以数据库为准；改密/登出/封禁/恢复/角色变更必须自增 `token_version`
- 敏感配置只放 `application-local.yaml`（gitignore）或 VM `.env`

## 待确认 / 阻塞

- SMTP 授权码未提供，验证码仅日志输出；生产 `log-code=false` 时无法投递，接入 SMTP 前不可上线
- 云服务器规格未定，部署阶段前确认
- 应用镜像与 Nginx/前端静态资源尚未联调（阶段 5/6）
