# 项目进度

更新日期：2026-11-17
设计文档版本：v0.16
当前阶段：阶段 2 完成；阶段 3 待开始（先读 `docs/stage-3-handoff.md`）

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
  - 密码策略：8~64 位且含字母与数字；验证码 6 位、5 分钟、一次性、场景隔离
  - 发码：同邮箱 60 秒冷却 + 每日 10 次；未注册邮箱返回 200 且不投递
  - 登录：BCrypt 在事务外，`recordLogin` 短事务；登出/改密/重置自增 `token_version`
  - 邮件：`log|smtp` 双实现；邮件失败只记 error、不改变响应；生产禁用 log 实现
  - 测试：Testcontainers 覆盖账号闭环；JaCoCo 行/分支 70% 门槛；时区按配置计算
- 阶段 2 鉴权与防护：
  - 登录锁定：账号 `login:fail:account:{email}` 5 次 / 15 分钟；IP `login:fail:ip:{ip}` 50 次 / 15 分钟；返回 40102；不存在账号同样计数；成功登录只清账号维度
  - 发码限流：邮箱 60 秒、每日 10 次；IP 每日 50 次；验证码错误 5 次后作废并返回 42900，重新发码清空失败计数
  - 弱密码：SecLists（MIT）Top 1000 内置字典，统一小写 + leet 还原；注册/重置/改密生效，登录不生效；命中返回 40000
  - 客户端 IP：`ClientIpResolver`，默认使用 `getRemoteAddr()`；生产开启 `trust-forwarded-for` 后只取 `X-Forwarded-For` 最后一跳并做字符校验
  - 安全测试：令牌失效矩阵、IDOR（忽略客户端 userId）、用户枚举响应一致、安全响应头、锁定/限流错误码
- 测试：99 个用例（JWT 5、JWT 过滤器 7、Web/安全 15、Auth 25、User 6、验证码 11、锁定 7、弱密码 7、IP 5、SMTP 3、集成 8）
- 覆盖率：JaCoCo 行 87.9%、分支 80.1%，`verify` 强制行/分支均不低于 70%

## 环境

- 本机：Windows 11、JDK 21（`D:\dev\jdk-21`）、Node 24；本机无 Docker
- Git Bash 里裸 `java` 仍是 17；运行应用用 `"$JAVA_HOME/bin/java" -jar target/*.jar` 或 `./mvnw`
- 开发虚拟机 `ssh user-center-vm`（192.168.216.130）：Docker 29.9.2
  - MySQL 8.4.13：宿主 `3306`，库 `usercenter`，账号 `usercenter`（密码在 VM `.env` 与 `application-local.yaml`）
  - Redis 7.4：宿主 `6380` → 容器 `6379`（VM 的 6379 被其他服务占用）
  - 启动：`ssh user-center-vm "cd ~/user-center && docker compose up -d mysql redis"`
- 种子管理员：`admin0001` / `admin@user-center.local`，密码见本地 `docs/decisions.md`（仅开发，部署前必须改）
- 部署：云服务器（已有，规格待确认；建议 2 核 4G）

## 验收结果（阶段 2）

- 本机 `./mvnw verify`：99 个测试（91 通过 + 8 个 Testcontainers 用例跳过），Spotless、Checkstyle、JaCoCo 通过
- VM Docker（2026-11-17）：`AuthFlowIntegrationTest` 8 个用例全部通过，`BUILD SUCCESS`（真实 MySQL/Redis）
- 账号锁定：连续 5 次错误密码后，第 6 次即使密码正确也返回 40102；成功登录后计数清零
- IP 锁定：同一 IP 对 50 个不同邮箱失败后，第 51 次返回 40102
- 验证码：错误 4 次返回 40001，第 5 次返回 42900 并作废验证码，随后正确码返回 40002
- 弱密码：`Password1`、`P@ssw0rd` 在注册/重置返回 40000，且不消费验证码
- 令牌：`token_version` 自增或账号封禁后旧令牌 `/users/me` 返回 401
- 枚举：不存在账号与错误密码的登录响应 code/message 完全一致

## 下一步（阶段 3 管理员）

1. 分页查询用户：`page`/`size` 上限 100，邮箱掩码，不返回密码哈希
2. 封禁/解封：按 `user_account` 操作，自增 `token_version`；禁止管理员封禁自己
3. 管理员重置密码：生成随机密码，仅响应一次，自增 `token_version`
4. 测试：管理员流程、普通用户 403、目标不存在 404、封禁后旧令牌失效、分页边界

## 交接

- 新会话先读本文件与 `docs/stage-3-handoff.md`，再按需读 `docs/plan.md` §7.3、§8.3 与 §11；设计变更先改文档再改代码
- 阶段 3 改动认证/账号状态后，按交接文档在 VM Docker 重跑 `AuthFlowIntegrationTest`；本机无 Docker 时跳过
- Git：阶段 2 在 `stage-2` 分支；`CLAUDE.md` 在主工作区仍有未提交裁剪，需决定提交或回退
- 课程文件在 `D:\teach\user-center-spring-security`（本地，不提交仓库）
- 关键约束：JWT 不含 role；角色变更/改密/登出/封禁/恢复/管理员重置必须自增 `token_version`；敏感配置只放 `application-local.yaml` 或 VM `.env`

## 待确认 / 阻塞

- 阶段 3 管理员接口尚未实现；范围与开放决策见 `docs/stage-3-handoff.md`
- SMTP 已实测（QQ 邮箱）；正式上线建议企业邮件 + SPF/DKIM/DMARC
- 云服务器规格未定，部署阶段前确认；应用镜像与 Nginx/前端静态资源尚未联调（阶段 5/6）
