# 编码规范

> 项目级规范，写代码前必读。机器能检查的交给工具，本文只写工具判断不了的约定。
> 相关：`CLAUDE.md`（项目规则与文档读取顺序）、`docs/plan.md`（设计）。

## 1. 命名

| 对象 | 规则 | 示例 |
|---|---|---|
| 类 | UpperCamel | `UserService`、`LoginParam` |
| 方法 / 变量 | lowerCamel | `findByEmail`、`tokenVersion` |
| 常量 | UPPER_SNAKE | `MAX_PAGE_SIZE` |
| 包 | 全小写 | `com.wwp.usercenter.security` |
| 布尔值 | `is/has/can` 前缀 | `isAdmin`、`hasPermission` |
| 数据库列 | snake_case | `user_account`、`create_time` |

- 类名使用名词，方法名使用动词或动词短语
- 禁止拼音缩写（`yhzh`、`mm`），除非是通用缩写（`url`、`id`、`dto`）
- 测试方法用 `should_xxx_when_yyy` 或 `xxx_yyy` 描述行为

## 2. 分层职责

| 层 | 可以做什么 | 不可以做什么 |
|---|---|---|
| controller | 参数绑定与校验、调用 service、组装返回 | 写业务分支、直接调用 mapper、拼 SQL |
| service | 业务规则、事务、调用多个 mapper | 处理 HTTP 对象、拼装响应体 |
| mapper | 单表/多表数据访问 | 写业务判断、抛业务异常 |
| security | 令牌解析、认证与授权判断 | 访问业务表以外的数据 |

- 账号状态判断统一使用 `User.isDeleted()` / `isBanned()` / `isActive()`，不要在 service/security 里重复比较枚举组合

## 3. 数据对象

- `entity`：与数据库表一一对应，只在 mapper/service 内部使用
- `param`：接口入参，带校验注解；不复用 entity
- `dto`：接口出参；不包含 `password_hash` 等敏感字段
- 转换集中在 service 或专门的 converter，不在 controller 里散落 `BeanUtils.copyProperties`

## 4. 异常与错误码

- 业务失败抛 `BusinessException(ErrorCode, message)`，由 `GlobalExceptionHandler` 统一转换
- 错误响应使用 RFC 9457 `ProblemDetail`（`application/problem+json`），业务码放 `code` 扩展字段；成功响应直接返回 DTO/record，不再包统一 envelope
- Spring MVC 自带的 400/405/406/415 也由 `ResponseEntityExceptionHandler` 统一映射，不能被兜底分支变成 500
- 错误码使用枚举，按模块分段：`400xx` 参数、`401xx` 认证、`403xx` 权限、`405xx` 方法、`406xx` 可接受性、`409xx` 冲突、`415xx` 媒体类型、`429xx` 限流、`500xx` 系统
- 不使用返回 `null` 或 `false` 表示失败
- 不吞异常；catch 后必须处理或包装再抛
- 对外错误信息不包含堆栈、SQL、内部路径

## 5. 参数校验

- controller 入参使用 `@Valid` / `@Validated`
- 基础规则用注解（`@NotBlank`、`@Email`、`@Size`、`@Pattern`）
- 重复出现的邮箱、验证码、强密码规则使用 `common/validation` 下的组合注解（`@ValidEmail`、`@EmailCode`、`@StrongPassword`），长度/正则从 `common/constant` 派生
- 跨字段规则放 service，并使用 `BusinessException` 给出明确错误码
- 分页参数必须有上限（`size <= 100`），默认值明确

## 6. 事务

- 事务注解只放在 service 的公开方法上
- 默认捕获 `RuntimeException` 回滚；受检异常需显式 `rollbackFor`
- 事务内不做 HTTP 调用、发邮件、发短信等外部 I/O
- 邮件通过有界线程池异步投递；请求线程只提交任务，队列满时回退到调用线程形成背压
- 缓存/消息等副作用放在事务提交后执行

## 7. 日志

- 使用 SLF4J：`log.info("user login, userId={}", userId)`
- 禁止字符串拼接日志
- 禁止记录密码、令牌、验证码、完整邮箱、手机号；验证码仅在开发 `log` 模式输出到本地日志，生产 `smtp` 模式禁止
- 邮件发送失败由通道实现抛 `EmailDeliveryException`；`EmailDispatchService` 在异步线程记录 `error` 日志与堆栈，但不得改变对外响应（避免可投递性差异变成用户枚举信号）
- 异常日志记录堆栈，业务日志记录关键标识（用户 ID、操作类型、结果）
- 级别：`error` 需要人处理；`warn` 异常但可恢复；`info` 关键业务节点；`debug` 排查细节

## 8. SQL 与 MyBatis

- SQL 关键字一律小写：`select` / `insert` / `update` / `where` / `create table` / `unique key`
- 简单单表 CRUD 与条件查询优先使用 MyBatis-Plus `BaseMapper` / Wrapper；不要为了统一而把所有写入都搬进 XML
- 禁止在 Java 代码中拼接 SQL，也禁止使用 `setSql`、`apply` 等原生 SQL 片段
- 需要数据库原子表达式（如 `token_version = token_version + 1`）、批量、多表或 Wrapper 无法清晰表达的场景，使用自定义 Mapper 方法，SQL 放 `src/main/resources/mapper/*.xml`
- 自定义 SQL 统一放 XML，不使用 `@Select` / `@Update` 等注解 SQL（`BaseMapper` 内置方法除外）
- 参数一律用 `#{}`；`${}` 仅允许用于表名、列名等无法参数化的白名单场景，且值必须来自代码常量，不得来自用户输入
- 需要数据库原子更新时（如 `token_version = token_version + 1`）直接写一条 update，禁止先查后改，避免并发丢更新
- 禁止 `select *`，明确列名
- 高频只读查询只取必要列；认证态投影写在 Mapper XML，避免每次请求加载密码哈希
- 分页查询必须有上限；深分页场景再考虑游标
- 唯一索引、联合索引按查询条件设计；索引变更需写明理由
- 表名、列名遇到保留字（如 `user`）使用反引号
- MyBatis XML 放在 `src/main/resources/mapper/`，namespace 与 Mapper 全限定名一致，语句 `id` 与 Mapper 方法名一致
- 结构变更统一用 Flyway：版本化脚本放 `src/main/resources/db/migration`，已执行的脚本不得修改，只能新增版本；开发种子放 `db/dev` 的可重复迁移，生产只加载 `db/migration`

## 9. 常量与枚举

- 状态、角色、验证码场景、错误码一律用枚举
- 枚举值数据库存 `tinyint`/`varchar`，代码中不出现 `1`、`0` 字面量
- 业务常量集中在 `common/constant`，不在方法里散落

## 10. 注释

- 注释解释「为什么」，不解释「做了什么」
- 公开接口写清参数、返回值、可能抛出的业务异常
- 复杂算法、非直观的索引设计、安全相关的取舍必须写注释
- 禁止保留被注释掉的死代码

## 11. 测试

- 单元测试：service 业务规则，Mockito 隔离依赖
- Web 层测试：MockMvc 覆盖参数校验、错误码、401/403
- 集成测试：Testcontainers 起真实 MySQL/Redis，覆盖注册、登录、改密、注销恢复
- 测试数据使用独立压测/测试库，不连接开发库
- 安全用例必须覆盖：越权、令牌失效、用户枚举、验证码跨场景复用、限流锁定

## 12. 前端（web/）

- TypeScript，禁止 `any`（确需时写明原因）
- Vue 3 `<script setup>` + Composition API
- ESLint + Prettier；提交前 `npm run lint`
- 接口层集中封装，不在组件里散落 `fetch`
- 错误提示统一从后端的 `code/message` 转换，不直接展示原始异常
- 令牌存储与传递方式由安全设计决定，不在日志或 URL 中出现

## 13. 提交与评审

- Conventional Commits：`feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`
- 一次提交只做一件事；格式化与业务改动分开提交
- 提交前运行 `./mvnw verify`（阶段 0 完成后）
- 评审自查清单：
  - 是否有越权路径未校验归属
  - 是否把实体直接返回
  - 是否有魔法值
  - 日志是否泄露敏感信息
  - 分页是否有上限
  - 自定义 SQL 是否都在 mapper XML；service 是否残留 `setSql` 或字符串拼接
  - SQL 是否全为参数绑定
  - 异常是否被吞掉
