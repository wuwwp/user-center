# 阶段 3 交接：管理员功能

> **状态：已完成（2026-11-19）。** 本机单元/MockMvc 与 VM 集成测试均通过。下一阶段见 `docs/stage-4-handoff.md`。
>
> 更新日期：2026-11-19
> 读者：下一个会话的 AI / 开发者
> 先读 `docs/progress.md`，再读本文；设计以 `docs/plan.md` §7.3、§8.3、§11 为准。

## 1. 阶段 2 结束状态

阶段 2 的鉴权与防护已落地：

- 登录失败账号/IP 双维度锁定；发码邮箱/IP 限流；验证码失败 5 次作废。
- 弱密码 Top 1000 字典在注册、重置、改密生效；登录不校验历史弱密码。
- 客户端 IP 只读 `getRemoteAddr()`；生产由 Tomcat RemoteIpValve 解析转发链，应用层不解析 XFF。
- 安全用例覆盖：令牌版本/状态失效、IDOR、用户枚举一致性、安全响应头、锁定与限流错误码。
- 错误响应用 RFC 9457 `ProblemDetail`，业务码在 `code` 扩展字段；成功响应直接返回 DTO，不再有统一 envelope。
- JWT 固定 HS256 并校验 issuer/audience；Bearer 前缀大小写不敏感。
- Flyway 管理表结构；开发种子在 `db/dev`，生产不创建默认管理员。
- 邮件由有界线程池异步投递，请求线程不再等待 SMTP。
- 本机 `./mvnw verify`：94 个单元/slice 用例通过 + 8 个 Testcontainers 用例由 Failsafe 执行（无 Docker 时跳过）；覆盖率门槛 80%。
- VM Docker 集成测试（2026-11-19）：Flyway V1+V2 成功；`AuthFlowIntegrationTest` 8/8、`AdminFlowIntegrationTest` 1/1 通过，`BUILD SUCCESS`。

## 2. 当前 Git / 工作区状态

- 阶段 2、生产化与阶段 3 已合并到 `main`（提交见 `git log`）。
- `CLAUDE.md` 已提交；工作区以 `git status` 为准。
- `docs/decisions.md` 是本地文件（gitignore），阶段 3 取舍见 §31，不提交。
- 应用当前未运行；VM 192.168.216.130 已恢复，MySQL/Redis 容器可用。

## 3. 阶段 3 范围

按 `docs/plan.md` §7.3：

1. `get /api/v1/admin/users`：分页查询用户。
2. `put /api/v1/admin/users/{account}/status`：封禁 / 解封。
3. `post /api/v1/admin/users/{account}/password/reset`：重置为随机密码。

约束：

- `/api/v1/admin/**` 已在 `SecurityConfig` 要求 `ROLE_ADMIN`；不要在每个方法里重复鉴权。
- `user_account` 是对外标识，按它查询；不要用自增 id 作为管理接口路径。
- 封禁、解封、管理员重置密码都必须自增 `token_version`，旧令牌立即失效。
- 管理端返回邮箱做掩码（`EmailUtils.mask`），不返回密码哈希。

不包含：细粒度 RBAC、操作审计表、批量操作、导出、管理员账号创建（仍由初始化脚本/运维 SQL 产生）。

## 4. 建议落点

### 4.1 分页查询

- 新增 `AdminController`、`AdminUserService` / `Impl`；controller 只校验参数与调用 service。
- 请求参数：`page`（从 1 开始，默认 1）、`size`（默认 10，最大 100），可选 `status`。
- 返回 `PageResult<UserSummaryDTO>`（新增通用分页 DTO 或放 `model/dto`），字段：`userAccount`、`nickname`、`emailMasked`、`role`、`status`、`createTime`、`lastLoginTime`。列表固定过滤未注销账号，因此不返回 `isDeleted`。
- 使用 MyBatis-Plus `Page` + Wrapper；禁止 `select *`，只取展示列；排序固定 `id desc` 或 `create_time desc`，避免随机顺序。
- 已注销用户是否展示先定决策：推荐默认过滤 `is_delete=0`，后续需要再加 `includeDeleted`。

### 4.2 封禁 / 解封

- 入参 `AdminUserStatusParam`：`status` 只允许 `NORMAL` / `BANNED`，用枚举校验，禁止魔法值。
- 按 `user_account` 查用户；不存在返回 `NOT_FOUND`（40400）。
- 状态未变化时可直接成功或返回参数错误，先定决策并写测试。
- 更新状态与自增 `token_version` 放在同一短事务；可新增 `UserMapper.updateStatusAndIncrementVersion` XML，避免先查后改。
- 目标用户不存在、已注销时的语义要明确：推荐注销账号不参与封禁/解封（404 或 40000）。
- 禁止管理员封禁自己（推荐），防止把自己锁在管理后台外。

### 4.3 管理员重置密码

- 生成至少 16 位随机密码，字符集覆盖大小写字母、数字、符号；使用 `SecureRandom`，保证至少一个字母和一个数字（满足 `PASSWORD_REGEXP`）。
- 不打印密码日志；响应体只返回一次明文（`userAccount` + `newPassword`），DTO 命名体现一次性语义。
- 事务内只做 `updatePassword`（既有方法已自增 `token_version`）；随机密码生成在事务外。
- 不通过邮件发送随机密码（当前没有该需求）；产品化时再加快递渠道。

### 4.4 校验与错误码

- 分页上限 100；`page < 1`、`size > 100` 返回 40000。
- 目标不存在 40400；普通用户访问管理接口 40300（Security 层）。
- 不要新增错误码覆盖以上场景；只有状态机确有新语义时才扩表。

## 5. 阶段 1/2 留下的护栏

- 账号状态：`User.isDeleted()` / `isBanned()` / `isActive()`。
- 密码哈希：`UserCredentialService.updatePassword` 已原子自增 `token_version`，不要另写一条普通 update。
- 认证查询：`UserMapper.selectAuthStateById` 只取认证列；当前用户查询用 `selectCurrentUserById`，管理列表不要复用认证投影。
- 401/403 响应：`SecurityErrorResponseWriter` 输出 ProblemDetail，401 带 `WWW-Authenticate: Bearer`；业务异常交给 `GlobalExceptionHandler`。
- 客户端 IP：`ClientIpResolver`；管理接口不需要额外限流，但登录入口已覆盖撞库。
- 日志：不打印密码、令牌、完整邮箱；管理员操作记录操作者 ID 与目标 ID。
- SQL：简单 CRUD 用 Wrapper；状态+版本原子更新放 XML；禁止 `setSql`、`apply`、字符串拼接。
- 表结构变更：只加 Flyway 新版本，不改已执行脚本；管理员列表索引/字段变更写 `V2__...sql`。
- 邮件：业务层调用 `EmailDispatchService.dispatch`，不要直接同步调用 `EmailSender`。
- 弱密码字典只用于用户设置密码，不要用于管理员生成的随机密码。

## 6. 测试与命令

本机：

```bash
./mvnw verify
```

VM Docker 集成测试：

```bash
docker run --rm --network host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v uc-m2:/root/.m2 -v ~/uc-it:/app -w /app \
  -e TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1 \
  maven:3.9-eclipse-temurin-21 \
  mvn -B -Dit.test=AuthFlowIntegrationTest verify
```

集成测试已交给 Failsafe；用 `-Dit.test` 指定类，`verify` 才会触发 `integration-test` 阶段。

阶段 3 建议新增 `AdminFlowIntegrationTest`，覆盖：管理员登录 → 分页查询 → 封禁 → 被禁用户旧令牌 401 / 登录 40103 → 解封 → 登录恢复 → 管理员重置密码 → 旧密码 40101、新密码可登录。

## 7. 开放决策（动手前先定）

1. 已注销用户在管理列表/封禁接口中的处理：过滤、展示、还是拒绝操作？
2. 管理员能否封禁其他管理员？能否封禁自己？（推荐禁止自己）
3. 状态未变化时返回成功还是 40000？
4. 随机密码长度/字符集；是否需要排除易混淆字符。
5. 列表是否需要关键字/状态过滤；排序字段。
6. 管理员重置密码后是否要求用户下次登录强制改密（本期大概率不做，写入演进）。

结论写入 `docs/decisions.md`（本地），不要只留在对话里。

阶段 3 已采用：已注销账号不展示且不可操作；管理员可封禁其他管理员、不能封禁自己；状态未变化幂等成功；随机密码 16 位并排除易混淆字符；列表按 id 倒序、可选 status；管理员重置后不强制改密。

## 8. 阶段 3 完成定义

- 三个管理接口可用，普通用户访问 403，未登录 401。
- 封禁/解封/管理员重置后旧令牌全部失效；登录返回正确的 40103。
- 分页有上限、邮箱掩码、无密码哈希。
- 单元 + Web + 集成测试覆盖上述路径；本机 `./mvnw verify` 通过，覆盖率不低于 80%。
- **已完成**：本机 111 个单元/slice 通过，行 87.4% / 分支 81.9%；VM 上 `AdminFlowIntegrationTest` 2/2、`AuthFlowIntegrationTest` 8/8 通过。
- 进度与本文同步更新；新增取舍写入 `docs/decisions.md` §31。
