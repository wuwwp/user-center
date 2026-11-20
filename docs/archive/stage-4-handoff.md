# 阶段 4 交接：注销与恢复（已完成）

> **状态：已完成（2026-11-19）。** 本机、CI 与集成测试均通过；下一阶段见 `docs/archive/stage-5-handoff.md`。
> 更新日期：2026-11-19
> 设计以 `docs/plan.md` §7.3、§8.2、§11 为准。

## 1. 完成内容

- `delete /api/v1/users/me`：登录态 + `DELETE` 场景验证码；逻辑注销 `is_delete=1`，同时 `token_version+1`。
- `post /api/v1/users/me/recover`：`RECOVER` 验证码 + 新密码；原子恢复 `is_delete=0`、`password_hash`、`token_version+1`。
- 注销后登录返回 40104；恢复后旧令牌全部 401；恢复成功后可正常登录新密码。
- 管理员自助注销返回 40300；封禁账号不能注销、不能恢复。
- Flyway `V3__admin_audit_log.sql`：管理员封禁/解封/重置密码与审计记录同事务。
- 测试：本机 130 个单元/slice + 13 个集成；CI run `44539442887` 全绿。

## 2. 已定决策

1. 注销要求验证码：新增 `EmailScene.DELETE` 和 `verify:delete:{email}`，不复用 `RESET`。
2. 管理员禁止自助注销：Service 判断角色返回 40300。
3. 注销只允许 `isActive()`；恢复只允许已注销且未封禁的账号。“封禁 + 已注销”视为不支持状态，避免管理端看不到、用户又恢复不了的死锁。
4. 重复注销：Service 层幂等成功；首次注销已自增 `token_version`，旧令牌重试会在过滤器得到 401，不会到达 Controller。
5. 恢复成功后清 `login:fail:account:{email}`；不清 IP 计数。忘记密码重置成功后也按同一规则清理。
6. 注销后的数据保留期限/匿名化暂不做，写入演进路线。
7. 管理员操作审计表用 Flyway V3 实现，范围是管理员封禁/解封/重置密码。

## 3. 代码落点

| 功能 | 位置 |
|---|---|
| 注销/恢复入口 | `UserController`：`DELETE /me`、`POST /me/recover` |
| 生命周期业务 | `AccountLifecycleServiceImpl` |
| 验证码场景 | `EmailScene.DELETE`；`AuthServiceImpl.shouldDeliver` |
| 注销/恢复 SQL | `UserMapper.xml`：`softDeleteAccount`、`recoverAccount` |
| 管理员写入 + 审计 | `AdminUserActionWriter` |
| 审计落库 | `AuditLogServiceImpl`、`AdminAuditLogMapper`、`AdminAuditLog` |
| 数据库迁移 | `V3__admin_audit_log.sql` |
| 测试 | `AccountLifecycleServiceImplTest`、`AccountLifecycleIntegrationTest`、`UserControllerTest`、`AdminUserActionWriterTest`、`AuditLogServiceImplTest` |

## 4. 事务与状态语义

- 注销 SQL：`set is_delete=1, token_version=token_version+1 where id=? and is_delete=0 and status=0`。
- 恢复 SQL：`set is_delete=0, password_hash=?, token_version=token_version+1 where id=? and is_delete=1 and status=0`。
- 恢复的 BCrypt 在事务外；SQL 单条原子完成状态、密码和令牌版本。
- 管理员封禁/解封/重置：`AdminUserActionWriter` 的短事务内完成业务写入 + 审计；审计失败一起回滚。
- 审计表只追加，不提供更新/删除接口；不记录密码、令牌、验证码、完整邮箱。

## 5. 测试与验收

本机：

```bash
./mvnw verify
```

结果：130 个单元/slice 通过；13 个集成用例在无 Docker 时跳过；Spotless、Checkstyle、JaCoCo 通过。

CI：

```bash
gh run list -R wuwwp/user-center -L 3
```

run `44539442887`：130 + 13 全通过，Flyway V1–V3 成功。

阶段 4 覆盖：

- 注销 → 旧令牌 401 → 登录 40104 → RECOVER 发码 → 恢复 → 新密码登录
- DELETE 码与 RESET 码场景隔离
- 管理员自助注销 403
- 封禁账号不能恢复（单元测试）
- 管理员审计写入与业务同事务

## 6. 未做 / 后续

- 注销后的数据保留、匿名化、物理清理：演进路线。
- 用户自助注销/恢复的事件没有写入 `admin_audit_log`（该表只记录管理员操作）；如需完整安全审计，后续可扩展 `actor_type` 或独立事件表。
- Redis `INCR + EXPIRE`、验证码 `GET + DEL` 的 Lua 原子化仍未做。
- 前端注销/恢复页面属于阶段 5。
