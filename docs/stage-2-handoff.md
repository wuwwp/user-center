# 阶段 2 交接：鉴权与防护

> 更新日期：2026-11-17
> 读者：下一个会话的 AI / 开发者
> 先读 `docs/progress.md`，再读本文；设计细节以 `docs/plan.md` §8.2、§8.3、§11 为准。

## 1. 阶段 1 结束状态

阶段 1 的接口与业务闭环已完成：注册、登录、登出、当前用户、修改密码、邮箱验证码重置。

已验证：

- 本机 `./mvnw verify` 退出码 0：50 个测试通过 + 1 个 Testcontainers 用例跳过（本机无 Docker）。
- JaCoCo：行 86.1%、分支 72.6%；Spotless、Checkstyle 通过。
- 提交 `8e0f16e` 已推送到 `origin/main`。

重构后补充验证：

- **2026-11-17 已在 VM Docker 上重跑 `AuthFlowIntegrationTest`：1 个用例通过，`BUILD SUCCESS`。**
- 真实 MySQL 8.4 + Redis 7.4 执行了 `UserMapper.selectAuthStateById`（XML 投影）和 `User.isActive()` 路径；阶段 1 的验收缺口已关闭。

## 2. 当前 Git / 工作区状态

- 本地 `main` 有 1 个未推送的阶段 1 收尾/交接提交；以 `git status` 为准。
- `CLAUDE.md` 有一段已裁剪但未提交的改动（删除可从 `pom.xml`/构建命令推导的“技术栈/常用命令”）；新会话开始前决定提交还是回退，避免脏工作区。
- `docs/decisions.md` 是本地文件（gitignore），保留历史取舍；不要提交。
- 应用当前未运行；VM 上 MySQL/Redis 容器保持运行。
- `.claude/worktrees/doctor-claude-md` 是之前清理留下的空目录，被进程占用，重启后可删。

## 3. 阶段 2 范围

按 `docs/plan.md`：

1. 登录失败 5 次锁定 15 分钟（账号 + IP 双维度）；登录/发码限流。
2. 弱密码 Top 1000 字典校验；验证码失败次数限制。
3. 客户端 IP 只信任 Nginx 追加的 `X-Forwarded-For` 最后一跳。
4. 安全用例补齐：令牌失效矩阵、越权、用户枚举响应一致性。

不包含：管理员接口（阶段 3）、注销/恢复（阶段 4）、refresh token、OAuth2/OIDC、微服务拆分。

## 4. 建议的落点

### 4.1 登录失败锁定

- 维度：账号（规范化邮箱）+ 客户端 IP，两个计数器都要有；只做账号维度会被换 IP 绕过，只做 IP 维度会误伤 NAT 用户。
- Redis 键建议按现有命名风格：
  - `login:fail:account:{email}`
  - `login:fail:ip:{ip}`
- 计数用 Redis 原子 `increment`；第一次 `INCR` 返回 1 时设置 TTL（15 分钟），不要先 `get` 再 `set`。
- 成功登录后清理账号维度计数（IP 维度可保留或按策略衰减）。
- 先查锁定状态，再执行 BCrypt；不存在的账号也要走同样的计数/锁定路径，避免通过“是否被锁”判断账号是否存在。
- 失败响应语义要定：
  - 现有 `ErrorCode.LOGIN_LOCKED`（40102）尚未使用；返回 40102 会明确告诉攻击者“账号被锁”，有枚举信号。
  - 更保守的做法是继续返回通用 40101，只在内部记录锁定；无论选哪种，都要写进 `docs/decisions.md` 并补测试。
- 阈值、锁定时长、TTL 放常量或配置，不在方法里写魔法值；时间相关用注入的 `Clock`。

### 4.2 登录/发码限流

- 现有发码限频：同邮箱 60 秒冷却 + 每日 10 次；阶段 2 增加 IP 维度，防止换邮箱刷同一 IP。
- 登录也要有 IP 维度，避免只靠账号锁定被分布式撞库绕过。
- 客户端 IP 解析：
  - 只信任 Nginx 追加的 `X-Forwarded-For` 最后一跳；
  - 不信任客户端自带头的全部内容；
  - 明确代理层配置，避免直连后端伪造头；
  - IPv6、代理链、空值要有兜底。
- 限流和锁定都应有单元测试 + 集成测试；注意 Redis 键的 TTL 与跨日边界。

### 4.3 弱密码 Top 1000

- 建议放 `src/main/resources` 的字典文件，启动时加载到不可变 `Set<String>`，缓存复用。
- 统一大小写/规范化后再比较；密码策略本身禁止空格，保持与 `ValidationConstants.PASSWORD_REGEXP` 一致。
- 应用在注册、验证码重置、修改密码；**不要应用在登录**——登录只验证已有凭证，不能因为历史密码弱就把老用户挡在门外。
- 失败返回现有 `PARAM_INVALID`（40000），消息明确但不泄露内部字典。
- 补边界测试：字典命中、大小写变体、刚好 8 位、包含字母数字但仍是弱密码。

### 4.4 验证码失败次数

- 建议键：`verify:fail:{scene}:{email}`，与现有场景隔离保持一致。
- 每次校验失败 `INCR`，首次设置 TTL；达到上限后拒绝，避免无限猜 6 位码。
- 需要明确“达到上限后是否删除验证码”：删除会让用户必须重新发码；保留则到期自然失效。两种都可，但要在文档和测试里固定。
- 失败计数与发码限频分开：发码限制的是“发送”，失败计数限制的是“猜测”。
- 校验通过后删除验证码和失败计数。

### 4.5 安全测试清单

- 令牌失效矩阵：改密、登出、封禁、恢复、角色变更后旧令牌都 401。
- 越权：`/api/v1/users/me` 只使用安全上下文中的 id，不接受客户端传 userId。
- 用户枚举：登录失败、发码、重置对“存在/不存在/封禁/注销”的响应保持一致；注册冲突是已接受的例外（见 `docs/decisions.md`）。
- 锁定/限流：账号维度、IP 维度、成功登录清计数、TTL。
- 验证码：跨场景复用、错误次数、过期、一次性消费。
- 弱密码：注册/重置/改密拒绝，登录不拒绝历史弱密码。
- 集成测试在 VM Docker 上跑通；本机无 Docker 时允许跳过。

## 5. 阶段 1 留下的护栏

新代码不要绕开这些入口：

- 账号状态：`User.isDeleted()` / `isBanned()` / `isActive()`。
- 登录/重置资格：`AuthServiceImpl.ensureAccountUsable`。
- 自助接口存在性：`UserServiceImpl.requireNotDeletedUser`（返回 404，不要复用认证的 40104）。
- 认证查询：`UserMapper.selectAuthStateById`，只取 `id/role/status/is_delete/token_version`；不要在过滤器里改回 `selectById`。
- 401/403 响应：`SecurityErrorResponseWriter.write`。
- 邮件失败：通道抛 `EmailDeliveryException`，认证流程只记 error、不改变响应；不要让 Spring `MailException` 泄漏到业务层。
- 校验：`@ValidEmail` / `@EmailCode` / `@StrongPassword`；登录保持宽松，只校验长度。
- 事务：BCrypt、SMTP 不进事务；短写入继续放 `UserCredentialService`。
- SQL：简单单表优先 Wrapper；原子/投影等自定义 SQL 放 `UserMapper.xml`；禁止 `setSql`、`apply` 和字符串拼接。
- 日志：不打印密码、令牌、验证码、完整邮箱。

## 6. 测试与命令

本机：

```bash
./mvnw verify
```

VM Docker 集成测试（沿用现有流程；具体命令见 `docs/progress.md` / 课程 Lesson 11 的 Testcontainers 部分）：

```bash
docker run --rm --network host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v uc-m2:/root/.m2 -v ~/uc-it:/app -w /app \
  -e TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1 \
  maven:3.9-eclipse-temurin-21 \
  mvn -B -Dtest=AuthFlowIntegrationTest test
```

重构后已在 VM 上跑通；阶段 2 每次改动认证链路后仍按此命令重跑，确认真实 SQL/Redis 链路没有回归。

## 7. 开放决策（动手前先定）

1. 锁定响应：40102 明确锁定，还是 40101 通用失败？推荐通用失败 + 内部锁定，避免枚举；若选 40102，需接受状态泄露。
2. IP 维度阈值：与账号维度相同（5 次/15 分钟）还是更宽？NAT/办公网误伤要评估。
3. 弱密码字典来源与许可证：仓库内自带 Top 1000，还是构建时下载？不能引入来源不明/许可证不清的列表。
4. 验证码达到失败上限后：删除验证码还是保留到 TTL？
5. 登录/发码限流返回码：42900 已有；锁定是否复用 42900 还是 40102，需要统一。
6. `LOGIN_LOCKED`（40102）是否保留：保留则阶段 2 使用，不保留则删除，避免死枚举。

这些决策写入 `docs/decisions.md`，不要只留在对话里。

## 8. 阶段 2 完成定义

- 账号/IP 双维度锁定与限流生效，成功登录清计数。
- 弱密码在注册/重置/改密生效，登录不误伤历史账号。
- 验证码失败次数限制生效，跨场景与一次性语义不变。
- 用户枚举响应一致性测试通过；注册冲突的例外仍与文档一致。
- 令牌失效矩阵、越权、锁定、限流、弱密码、验证码失败均有测试。
- 本机 `./mvnw verify` 通过；VM Docker 集成测试通过；覆盖率不低于 70%。
- 新增取舍写入 `docs/decisions.md`，进度与本文同步更新。
