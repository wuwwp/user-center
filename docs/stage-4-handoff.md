# 阶段 4 交接：注销与恢复

> 更新日期：2026-11-19
> 读者：下一个会话的 AI / 开发者
> 先读 `docs/progress.md`，再读本文；设计以 `docs/plan.md` §7.3、§8.2、§11 为准。

## 1. 阶段 3 结束状态

- 管理端三个接口已完成：分页查询、封禁/解封、随机密码重置。
- 管理员列表只取展示列、邮箱掩码；封禁/解封/重置都自增 `token_version`。
- 普通用户访问 `/api/v1/admin/**` 返回 403；禁止管理员封禁自己。
- 本机 `./mvnw verify`：111 个单元/slice 通过 + 10 个集成用例由 Failsafe 执行（本机无 Docker 跳过）；覆盖率行 87.4% / 分支 81.9%。
- 阶段 3 集成测试已在 VM 通过：`AdminFlowIntegrationTest` 1/1、`AuthFlowIntegrationTest` 8/8。

## 2. 当前 Git / 工作区状态

- 阶段 3 已合回 `main`；`CLAUDE.md` 已提交。
- `docs/decisions.md` 是本地文件（gitignore），阶段 3 取舍见 §31。
- 应用当前未运行；VM 上 MySQL/Redis 容器可用。
- 阶段 4 代码尚未开始。

## 3. 阶段 4 范围

按 `docs/plan.md` §7.3：

1. `delete /api/v1/users/me`：当前用户注销。
2. `post /api/v1/users/me/recover`：邮箱验证码恢复已注销账号。

约束：

- 注销是**逻辑删除**（`is_delete=1`），行保留供恢复；不物理删除。
- 注销、恢复都必须自增 `token_version`，旧令牌立即失效。
- 注销后登录：凭证正确时返回 40104（`ACCOUNT_DELETED`），不能泄露给未验证凭证的请求。
- 恢复必须走 `RECOVER` 场景验证码；禁止跨场景复用。
- 恢复需要设置新密码，应用弱密码字典。
- 管理员账号不能自助注销（推荐）。
- 已注销账号不出现在管理员列表，管理员封禁/重置接口也不操作它。

## 4. 建议落点

### 4.1 注销

- 新增 `AccountLifecycleService` / `Impl`，或放在 `UserServiceImpl`；controller 只调用。
- 入参先定：仅登录态直接注销，还是要求再次输入密码/验证码。推荐至少要求密码，防止令牌被盗后直接注销；若本期简化，至少写进 decisions。
- 原子 SQL 放 `UserMapper.xml`：
  ```sql
  update `user`
  set is_delete = 1, token_version = token_version + 1
  where id = #{userId} and is_delete = 0
  ```
- 管理员角色判断：`role == ADMIN` 抛 `FORBIDDEN` 或 `PARAM_INVALID`；不要允许管理员把自己删掉。
- 已注销用户重复注销：幂等成功或 404，先定并写测试。
- 日志记录 userId，不打印邮箱/密码。

### 4.2 恢复

- 入参 `RecoverAccountParam`：`email`、`code`、`newPassword`；复用 `@ValidEmail`、`@EmailCode`、`@StrongPassword`。
- 流程：弱密码校验 → `verifyAndConsume(email, RECOVER, code)` → 查用户 → 必须是已注销账号 → 封禁账号拒绝 → 更新密码、`is_delete=0`、`token_version+1`。
- 原子 SQL 放 `UserMapper.xml`：
  ```sql
  update `user`
  set is_delete = 0,
      password_hash = #{passwordHash},
      token_version = token_version + 1
  where id = #{userId} and is_delete = 1
  ```
- 未注销/不存在账号：验证码通过后返回统一错误，避免枚举；发码接口 `shouldDeliver(RECOVER)` 已只对已注销账号投递。
- 恢复后是否需要重新登录：是，旧令牌全部失效。
- 恢复后登录失败计数：建议清除账号维度计数。

### 4.3 验证码与安全

- `EmailScene.RECOVER` 已存在，发码场景隔离和失败次数限制沿用阶段 2。
- 恢复接口公开，受发码限频和验证码失败次数保护；不需要额外 IP 限流，但可复用 `ClientIpResolver` 记录。
- 注销/恢复都写审计日志（如果阶段 3 的审计表还没做，可在本阶段补 Flyway V3）。

## 5. 阶段 1–3 留下的护栏

- 账号状态：`User.isDeleted()` / `isBanned()` / `isActive()`。
- 认证资格：`AuthServiceImpl.ensureAccountUsable`（已注销 40104、封禁 40103）。
- 当前用户资料：`UserMapper.selectCurrentUserById`，不要加载密码哈希。
- 管理端状态：`UserMapper.selectAdminStateByAccount` / `updateStatusAndIncrementTokenVersion`。
- 密码写入：`UserCredentialService.updatePassword` 已原子自增 `token_version`；恢复如果同时改 `is_delete`，需要新的原子 SQL。
- 错误：`BusinessException` → `ProblemDetail`；成功响应直接返回 DTO。
- 表结构：Flyway 新版本，不改已执行脚本；如需审计表写 `V3__...sql`。
- 日志：不打印密码、令牌、验证码、完整邮箱。

## 6. 测试与命令

本机：

```bash
./mvnw verify
```

VM Docker：

```bash
docker run --rm --network host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v uc-m2:/root/.m2 -v ~/uc-it:/app -w /app \
  -e TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1 \
  maven:3.9-eclipse-temurin-21 \
  mvn -B -Dit.test=AdminFlowIntegrationTest,AuthFlowIntegrationTest verify
```

阶段 4 建议新增 `AccountLifecycleIntegrationTest`：注册 → 注销 → 旧令牌 401 → 登录 40104 → 发 recover 码 → 恢复 → 登录成功 → 旧令牌仍 401；另测验证码跨场景、弱密码、封禁账号不可恢复、管理员不可注销。

## 7. 开放决策（动手前先定）

1. 注销是否需要再次输入密码/验证码，还是登录态直接注销？
2. 管理员账号是否禁止自助注销？推荐禁止。
3. 封禁的已注销账号能否恢复？推荐禁止，先由管理员解封。
4. 重复注销返回成功还是 404？
5. 恢复后是否清除登录失败计数？
6. 注销后的数据保留期限/匿名化是否本期做？建议写入演进路线。
7. 管理员操作审计表是否在阶段 4 一并做（Flyway V3）？

结论写入 `docs/decisions.md`，不要只留在对话里。

## 8. 阶段 4 完成定义

- `delete /users/me` 与 `post /users/me/recover` 可用，注销后登录 40104，旧令牌 401。
- 恢复需要 RECOVER 场景验证码 + 新密码，恢复后旧令牌失效。
- 管理员不能自助注销；封禁账号不能自助恢复；已注销账号不出现在管理员列表。
- 单元 + Web + 集成测试覆盖；本机 `./mvnw verify` 与 VM Docker 集成测试通过，覆盖率不低于 80%。
- 阶段 3 的 `AdminFlowIntegrationTest` 已在 VM 通过（1/1）。
- 进度与本文同步更新；新增取舍写入 `docs/decisions.md`。
