# 用户中心（统一认证服务）设计文档

## 文档信息

| 项目 | 内容 |
|---|---|
| 文档名称 | 用户中心（统一认证服务）设计文档 |
| 文档版本 | v0.13 |
| 文档状态 | 评审中 |
| 编写日期 | 2026-11-13 |
| 适用范围 | user-center 服务与配套 Web 前端的设计、开发、测试与部署 |
| 密级 | 公开 |

## 修订记录

| 版本 | 日期 | 修订人 | 修订说明 |
|---|---|---|---|
| v0.1 | 2026-11-12 | — | 初稿：确认架构、认证方案、数据模型、接口与实施阶段 |
| v0.2 | 2026-11-13 | — | 数据库升级 MySQL 8.4 LTS；补充安全细节与阶段调整 |
| v0.3 | 2026-11-13 | — | 去除过度设计：鉴权状态改查库、精简字段、移除密钥轮换实现与硬性性能门槛；增加 Web 前端、真实 SMTP、压测报告与 CI |
| v0.4 | 2026-11-13 | — | 补充代码质量与规范：Spotless + Checkstyle、ESLint + Prettier + TypeScript、pre-commit 钩子、CI 强制检查、Conventional Commits |
| v0.5 | 2026-11-15 | — | JWT 去掉 role claim，角色以数据库为准；明确单体内聚、业务权限下沉、introspection 延后到接入阶段 |
| v0.6 | 2026-11-15 | — | 阶段 1 登录事务边界：BCrypt 校验放在事务外，短事务只包 `last_login_time` 更新 |
| v0.7 | 2026-11-15 | — | 阶段 1 实现补充：新增验证码重置密码接口；明确验证码位数/有效期/限发与场景枚举；明确 `user_account` 生成规则 |
| v0.8 | 2026-11-16 | — | 演进路线新增「邮箱验证码登录（passwordless + 首次登录自动开户）」候选，暂不排期 |
| v0.9 | 2026-11-16 | — | 时间处理：业务日期按配置时区（默认 `Asia/Shanghai`）计算，容器固定 `TZ`；修正如「未注册邮箱不写码」的旧表述 |
| v0.10 | 2026-11-16 | — | SQL 收口：自定义 SQL 统一放 `UserMapper.xml`，禁止 `setSql` 等原生片段；`token_version` 原子自增改为显式 update |
| v0.11 | 2026-11-16 | — | 修正 SQL 规范：简单 CRUD 继续用 Wrapper；仅原子更新等需要数据库表达式的场景使用自定义 SQL |
| v0.12 | 2026-11-16 | — | 明确用户枚举取舍：登录/发码/重置统一响应，注册返回 409 并记录理由（decisions.md §25） |
| v0.13 | 2026-11-16 | — | SMTP 接入：log/smtp 双实现；邮件失败只记日志、不改变响应；生产默认 smtp 且缺配置启动失败 |

## 1. 引言

### 1.1 目的

本文档定义用户中心服务与配套 Web 前端的目标、范围、架构、数据模型、接口、安全要求、实施计划与验收标准，作为开发、测试和部署的依据。

### 1.2 术语

| 术语 | 说明 |
|---|---|
| 认证中心 | 统一提供注册、登录、令牌签发与校验的独立服务 |
| 访问令牌 | 登录成功后签发的 JWT，客户端以 Bearer 方式携带 |
| 逻辑删除 | 通过 `is_delete` 标记注销，数据保留 |
| 恢复 | 已注销账号在验证身份后重新启用 |
| 封禁 | 管理员对账号的禁用状态，与注销相互独立 |
| 用户枚举 | 通过接口响应差异推断某邮箱/账号是否存在 |
| 令牌版本 | 用户维度的整数，令牌签发时写入，用于整体吊销 |
| P95 | 按耗时排序后第 95% 位置的请求耗时，用于观察长尾延迟 |

## 2. 范围

### 2.1 目标

1. 为多个自有 Web 项目提供统一账号与登录能力
2. 提供管理员对用户的管理能力（查询、封禁/解封、重置密码）
3. 以独立单体服务交付，鉴权无状态，可平滑演进为 OAuth2/SSO 与微服务
4. 提供可访问的 Web 界面（注册、登录、个人中心、管理后台）
5. 工程实践完整：分层、统一返回、异常处理、参数校验、接口文档、自动化测试、容器化部署、CI、压测报告

### 2.2 非目标

- 图形验证码、第三方登录、OAuth2/OIDC 授权服务器
- RBAC 细粒度权限、多租户、项目级权限
- 短信验证码（保留 Provider 接口，不实现）
- 头像上传、手机号绑定/换绑
- 首次登录强制改密、审计日志、单端登出
- 数据归档与物理清理
- 微服务拆分、注册中心、网关

## 3. 总体设计

### 3.1 架构

```
浏览器（Vue 3 前端，Nginx 托管）
   │
   └── HTTPS ──> Nginx ──┬── /          → 静态前端
                         └── /api/v1/** → user-center（单体服务）
                                              ├── MySQL 8.4 LTS（用户数据，事实来源）
                                              └── Redis 7.x（验证码、限流、失败计数）
```

- 服务无状态，可水平扩展
- 前端与后端运行时分离，部署在同一台服务器的 Nginx 下
- 管理接口独立鉴权

### 3.2 技术选型

| 领域 | 选型 | 说明 |
|---|---|---|
| 运行时 | Java 21（LTS） | 启用虚拟线程 |
| 后端框架 | Spring Boot 4.1.x / Spring MVC | 与现有工程基线一致 |
| 安全 | Spring Security 7 + JWT（jjwt） | 标准鉴权链，便于后续接入 OAuth2 |
| 持久层 | MyBatis-Plus 3.5.18 + MySQL 8.4 LTS | 参数绑定，无字符串拼接 SQL |
| 缓存 | Redis 7.2+ | 验证码、发码限流、登录失败计数 |
| 前端 | Vue 3 + Element Plus + Vite | 注册/登录、个人中心、管理后台 |
| 参数校验 | spring-boot-starter-validation | 注解校验 + 全局异常转换 |
| 邮件 | spring-boot-starter-mail | 开发环境日志实现，生产环境 SMTP |
| 接口文档 | springdoc-openapi 3.2.0 | OpenAPI 3 + Swagger UI |
| 可观测性 | Spring Boot Actuator | 健康检查与基础指标 |
| 测试 | JUnit 5、Mockito、MockMvc、Testcontainers | 单元 + 接口 + 集成 |
| 压测 | k6 | 登录场景脚本与结果报告 |
| 代码质量 | Spotless + Checkstyle（SpotBugs 可选）、ESLint + Prettier | 格式化与静态检查，CI 强制 |
| CI | GitHub Actions | 编译、单元测试、集成测试 |
| 部署 | Docker / Docker Compose + Nginx | 应用、数据库、缓存、静态前端 |

**数据库版本说明**：MySQL 8.0 的 Premier Support 已于 2026-04-30 结束，本项目新部署统一使用 8.4 LTS；本地历史 8.0 实例不用于验收。

**虚拟线程说明**：`spring.threads.virtual.enabled=true`，由 Spring Boot 自动配置 Tomcat 与 `@Async` 执行器。

- 一个请求从头到尾运行在同一个虚拟线程上，`ThreadLocal`（含 Spring Security 上下文、MDC）正常工作，**不要**配置 `MODE_INHERITABLETHREADLOCAL`
- Java 21 下 `synchronized` 会钉住载体线程（JEP 491 在 Java 24 修复），应用热路径优先使用 `ReentrantLock`
- 连接池大小仍按数据库承载能力配置，虚拟线程不改变数据库并发上限，不是扩容手段

## 4. 功能需求

### 4.1 服务端

| 编号 | 功能 | 说明 | 优先级 |
|---|---|---|---|
| FR-01 | 邮箱注册 | 邮箱验证码校验，自动分配用户名，密码 BCrypt 存储，固定 `role=0` | 高 |
| FR-02 | 登录 | 邮箱 + 密码，签发 JWT | 高 |
| FR-03 | 登出 | 令牌版本自增，当前用户所有令牌失效 | 高 |
| FR-04 | 当前用户 | 返回脱敏后的用户信息 | 高 |
| FR-05 | 修改密码 | 校验旧密码，成功后所有令牌失效 | 高 |
| FR-06 | 忘记密码 | 邮箱验证码重置密码 | 高 |
| FR-07 | 注销 | 密码确认，逻辑删除，令牌失效，管理员不可自助注销 | 中 |
| FR-08 | 恢复 | 原密码或邮箱验证码验证后恢复账号，令牌版本自增 | 中 |
| FR-09 | 管理员查询 | 按用户名/邮箱/状态分页查询 | 高 |
| FR-10 | 管理员封禁/解封 | 封禁后旧令牌失效，登录被拒 | 高 |
| FR-11 | 管理员重置密码 | 生成随机密码一次性返回，不记录日志，令牌版本自增 | 高 |
| FR-12 | 验证码发送 | 按场景隔离，限频发送，开发环境输出到日志 | 高 |

### 4.2 Web 界面

| 编号 | 页面 | 内容 |
|---|---|---|
| UI-01 | 注册 / 登录 | 邮箱注册与登录，验证码倒计时 |
| UI-02 | 个人中心 | 查看资料、修改密码、重置密码、注销账号 |
| UI-03 | 管理后台 | 用户分页列表、搜索、封禁/解封、重置密码；仅管理员可见 |

## 5. 非功能需求

| 编号 | 类别 | 要求 |
|---|---|---|
| NFR-01 | 安全 | 全站 HTTPS；密码不可逆存储；令牌可吊销；防用户枚举；管理接口独立鉴权；安全响应头 |
| NFR-02 | 性能 | 分页接口单页上限 100 条；提供登录压测脚本与结果报告，不设硬性性能门槛 |
| NFR-03 | 可用性 | 服务无状态，可多实例部署；MySQL/Redis 故障时返回明确错误，不泄露内部细节 |
| NFR-04 | 可维护性 | 分层清晰；统一返回与错误码；配置与代码分离；敏感配置不进仓库 |
| NFR-05 | 可观测性 | 结构化日志，敏感字段脱敏；Actuator 健康检查；关键操作记录用户 ID 与结果 |
| NFR-06 | 兼容性 | API 带版本前缀 `/api/v1`，破坏性变更升版本 |

## 6. 数据设计

### 6.1 用户表

```sql
create table `user` (
    id              bigint unsigned auto_increment primary key,
    user_account    varchar(32)  not null comment '对外标识，自动生成，不参与登录',
    nickname        varchar(32)  null     comment '昵称',
    email           varchar(128) not null comment '登录标识',
    password_hash   varchar(100) not null comment 'BCrypt',
    role            tinyint      not null default 0 comment '0-普通用户 1-管理员',
    status          tinyint      not null default 0 comment '0-正常 1-封禁',
    is_delete       tinyint      not null default 0 comment '0-未注销 1-已注销',
    token_version   int          not null default 0,
    last_login_time datetime     null,
    create_time     datetime     not null default current_timestamp,
    update_time     datetime     not null default current_timestamp on update current_timestamp,
    unique key uk_email (email),
    unique key uk_user_account (user_account)
) engine = innodb default charset = utf8mb4;
```

### 6.2 设计说明

- `id` 为内部代理键，不对外暴露；对外使用 `user_account`
- `email` 为登录标识，唯一；`user_account` 自动生成，唯一，作为展示与检索标识
  - 生成规则：`u` + 8 位小写字母数字随机串（如 `u3f7a9c2d`），不参与登录；依赖唯一索引兜底，冲突时重新生成
- 枚举字段在代码层用枚举类绑定，禁止魔法值
- 注销为逻辑删除，保留数据以支持恢复；唯一索引不释放标识，注销后同邮箱注册引导至恢复流程
- `token_version` 与 JWT 中的 `ver` 比对，实现改密、封禁、注销、恢复、管理员重置密码后旧令牌立即失效；角色变更同样自增
- `last_login_time` 用于登录审计与异常检测

### 6.3 鉴权状态查询

- 每个认证请求校验 JWT 签名与有效期后，按主键查询一次用户行，比对 `token_version` 与 `status`，并以数据库 `role` 生成权限
- 不引入 Redis 或本地缓存：主键查询成本低，避免缓存一致性带来的越权与误封风险
- 若未来出现明确的性能瓶颈，再按「先测量、后缓存」的原则引入缓存并配套失效策略

### 6.4 数据生命周期

| 阶段 | 状态 | 数据 |
|---|---|---|
| 注册 | `is_delete=0` | 全部保留 |
| 注销 | `is_delete=1` | 保留，可恢复 |
| 封禁 | `status=1` | 保留，不可登录，不可自助恢复 |
| 恢复 | `is_delete=0` | 保留原数据与角色；`token_version` 自增 |

归档与物理清理不在本期范围，见演进路线。

## 7. 接口设计

### 7.1 通用约定

- 基础路径：`/api/v1`
- 认证头：`Authorization: Bearer <token>`
- 返回体：`{"code": 0, "message": "ok", "data": {...}}`
- 分页参数：`page`（从 1 开始）、`size`（默认 10，最大 100）
- 时间格式：ISO-8601

### 7.2 错误码

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 40000 | 400 | 参数校验失败 |
| 40001 | 400 | 验证码错误 |
| 40002 | 400 | 验证码已过期 |
| 40100 | 401 | 未登录或令牌无效 |
| 40101 | 401 | 邮箱或密码错误 |
| 40102 | 401 | 登录失败次数过多，账号锁定 |
| 40103 | 401 | 账号已封禁（仅在凭证验证通过后返回） |
| 40104 | 401 | 账号已注销（仅在凭证验证通过后返回） |
| 40300 | 403 | 无权限 |
| 40400 | 404 | 资源不存在 |
| 40900 | 409 | 资源冲突（邮箱已注册等） |
| 42900 | 429 | 请求过于频繁 |
| 50000 | 500 | 系统错误 |

### 7.3 接口清单

#### 认证

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| post | `/api/v1/auth/register` | 邮箱验证码注册 | 否 |
| post | `/api/v1/auth/login` | 邮箱 + 密码登录 | 否 |
| post | `/api/v1/auth/logout` | 登出 | 是 |
| post | `/api/v1/auth/email/code` | 按场景发送验证码（register/reset/recover） | 否 |
| post | `/api/v1/auth/password/reset` | 邮箱验证码重置密码（忘记密码） | 否 |

#### 用户

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| get | `/api/v1/users/me` | 当前用户信息 | 是 |
| put | `/api/v1/users/me/password` | 修改密码 | 是 |
| post | `/api/v1/users/me/recover` | 恢复已注销账号 | 否 |
| delete | `/api/v1/users/me` | 注销账号 | 是 |

#### 管理员

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| get | `/api/v1/admin/users` | 分页查询用户 | 管理员 |
| put | `/api/v1/admin/users/{account}/status` | 封禁 / 解封 | 管理员 |
| post | `/api/v1/admin/users/{account}/password/reset` | 重置为随机密码 | 管理员 |

## 8. 安全设计

### 8.1 认证与令牌

- JWT 使用 HS256，密钥 ≥ 256 位，仅存于环境变量或本地配置
- 校验固定算法，拒绝 `none` 与算法替换
- claims 仅包含 `sub`(用户 ID)、`ver`(令牌版本)、`jti`、`iat`、`exp`、`iss`；**不包含 role**，角色以数据库为准
- 有效期 2 小时，不提供刷新令牌；过期后重新登录
- 每个请求校验签名、有效期，并查询用户行比对 `ver` 与 `status`；角色从数据库读取后生成 authority
- 作用域：当前令牌仅用于本服务；其它项目接入时通过 `/internal/introspect` 校验，不共享 HS256 密钥；后续多服务本地验签时切换 RS256（认证服务持私钥签发，业务服务持公钥验签）

### 8.2 账号与密码

- 密码 BCrypt（cost 10+），永不返回、不写日志
- 密码策略：8~64 位，至少包含字母与数字；校验 Top 1000 弱密码字典
- 登录、发码、验证码重置统一响应，避免用户枚举；注册冲突明确返回 409，接受注册环节的枚举（理由见 decisions.md §25）
- 登录流程对不存在的账号执行一次固定哈希的 BCrypt 比对，抹平计算路径差异；不依赖随机延时
- 事务边界：密码校验（BCrypt）在事务外执行，事务只包住 `last_login_time` 更新等短写入，避免哈希计算占用数据库连接
- 登录失败 5 次锁定 15 分钟；验证码同邮箱 60 秒限发、每日 10 次
- 验证码：6 位数字，有效期 5 分钟，一次性使用（校验通过即删除）
- 验证码按业务场景隔离：`verify:register:{email}`、`verify:reset:{email}`、`verify:recover:{email}`，校验时严格匹配场景，禁止跨场景复用
- 发码限频：同邮箱 60 秒一次（`verify:cooldown:{email}`），每日 10 次（`verify:daily:{email}:{date}`）
- 时间与时区：业务日期按 `user-center.time-zone`（默认 `Asia/Shanghai`）计算，通过注入 `Clock` 使用；容器显式设置 `TZ=Asia/Shanghai`，不依赖镜像默认 UTC
- SQL 分层：简单单表 CRUD 继续用 Wrapper；`token_version` 原子自增等需要数据库表达式的写入放 `UserMapper.xml`；service 不拼 SQL
- 邮件发送：`user-center.mail.mode=log|smtp` 二选一；SMTP 故障只记录 error 日志、不改变对外响应，避免「可投递/不可投递」差异变成枚举信号；生产默认 smtp，缺少配置时启动失败
- 发码接口统一返回成功，不暴露邮箱是否注册；未注册邮箱照常限频与写码，但不投递，后续校验自然失败
- 登录失败、发码同时做账号维度与 IP 维度计数；客户端 IP 仅信任来自 Nginx 的 `X-Forwarded-For`，且只取可信代理追加的最后一跳

### 8.3 权限

- 管理接口要求 `role=1`，按路径前缀统一配置
- 用户资源操作校验归属，防越权（IDOR）
- 角色变更、封禁、注销、恢复、管理员重置密码均自增 `token_version`，避免旧令牌保留旧权限
- 注册接口固定写入 `role=0`；管理员只能由初始化脚本或运维 SQL 产生

### 8.4 数据与传输

- 接口返回 DTO，管理端返回的邮箱做掩码
- SQL 全部参数绑定，禁止字符串拼接
- 全站 HTTPS；CORS 白名单
- 安全响应头：`Strict-Transport-Security`、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`
- 无 Cookie 认证，不依赖 CSRF Token
- 敏感配置（数据库密码、JWT 密钥、SMTP 授权码）仅存本地/环境变量，已加入 `.gitignore`

### 8.5 日志与可观测性

- 记录登录结果、封禁、重置密码等事件，包含操作者 ID 与目标 ID
- 不记录密码、令牌、验证码、邮箱完整地址（脱敏）
- Actuator 仅暴露 `health`、`info`，其余端点关闭或鉴权
- 指标与告警接入不在本期范围

## 9. 环境与部署

### 9.1 环境

| 环境 | 用途 | 组件 |
|---|---|---|
| local | 本地开发 | 开发机 JDK 21；MySQL 8.4、Redis 7.x 运行于开发虚拟机 Docker |
| test | 自动化测试 | Testcontainers（MySQL 8.4 + Redis 7.x） |
| prod | 部署 | 云服务器 + Docker Compose |

### 9.2 部署拓扑

```
Nginx（HTTPS、静态前端、反向代理、可信 X-Forwarded-For）
  ├── /            → web/dist（Vue 3 构建产物）
  └── /api/v1/**   → user-center（容器，Java 21）
                        ├── MySQL 8.4 LTS（容器，数据卷持久化）
                        └── Redis 7.x（容器，AOF 持久化）
```

### 9.3 交付物

- `Dockerfile`：多阶段构建，运行镜像使用 JRE 21
- `docker-compose.yml`：应用、MySQL、Redis、Nginx 编排，配置通过环境变量注入
- `.env.example`：完整配置项模板，不含真实凭据
- `web/`：Vue 3 前端源码与构建说明
- `.editorconfig`、`checkstyle.xml`、`.githooks/pre-commit`
- 部署说明：初始化 SQL、启动步骤、健康检查
- `.github/workflows/ci.yml`：编译、单元测试、集成测试（Testcontainers）
- `perf/login.js`、`docs/perf.md`：压测脚本与结果报告
- 部署方式：CI 通过后手动执行 `docker compose pull && docker compose up -d`

## 10. 测试策略

| 层级 | 工具 | 覆盖内容 |
|---|---|---|
| 单元测试 | JUnit 5 + Mockito | 注册冲突、锁定、恢复条件、权限判断等业务规则 |
| Web 层测试 | MockMvc | 参数校验、错误码、401/403 鉴权矩阵 |
| 集成测试 | Spring Boot Test + Testcontainers | 真实 MySQL/Redis 下的注册、登录、改密、注销恢复全链路 |
| 压测 | k6 | 登录场景吞吐与 P50/P95/P99，脚本与结果进仓库，不进 CI |
| 接口验收 | Swagger / OpenAPI + 测试用例表 | 手工验收与回归 |

安全测试用例（必须覆盖）：

1. 未认证访问受保护接口返回 401
2. 普通用户访问管理接口返回 403
3. 修改密码、封禁、注销、恢复后旧令牌失效
4. 越权访问他人资源被拒绝
5. 用户枚举：不存在账号与密码错误返回一致
6. 验证码跨场景复用被拒绝
7. 登录失败锁定与发码频率限制生效
8. 登出后令牌失效

核心业务分支覆盖率不低于 70%，安全用例要求 100% 覆盖。

压测说明：使用独立压测库，每个账号使用不同密码并独立生成哈希；脚本包含预热阶段，报告注明环境、参数、稳态吞吐与长尾延迟。

## 11. 实施计划

| 阶段 | 内容 | 交付物 | 验收标准 |
|---|---|---|---|
| 阶段 0 工程基础 | 依赖与配置分层；统一返回/异常/校验；Spring Security 基础配置与 JWT Filter 骨架；表结构与初始化脚本；Docker 编排骨架 | 可启动工程、`init.sql`、安全骨架 | 启动成功；未认证访问返回 401；种子管理员可登录 |
| 阶段 1 账号核心 | 注册、登录（密码校验在事务外，短事务只包写入）、登出、当前用户、修改密码、邮箱验证码重置 | 认证接口与单元测试 | 注册→登录→改密→重置全流程通过 |
| 阶段 2 鉴权与防护 | 角色校验、令牌版本与状态校验、场景化验证码、账号/IP 限流与锁定、弱密码校验、安全响应头 | 安全配置与鉴权测试 | 安全测试用例全部通过 |
| 阶段 3 管理员 | 分页查询、封禁/解封、重置密码 | 管理接口与测试 | 管理员流程通过，普通用户不可访问 |
| 阶段 4 注销与恢复 | 注销、恢复流程、状态校验 | 完整闭环与测试 | 注销→登录提示→恢复→旧令牌失效 |
| 阶段 5 Web 前端 | Vue 3 页面、接口对接、权限控制 | `web/` 与构建说明 | 注册登录、个人中心、管理后台可用 |
| 阶段 6 收尾 | OpenAPI 导出、README、Dockerfile/compose、CI、压测报告、验收 | 可部署产物与文档 | 新环境一条命令启动；CI 全绿；压测报告可复现 |

## 12. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| Spring Security 7 / Boot 4 资料少 | 开发阻塞 | 以官方文档为准，安全配置集中管理 |
| 逻辑删除与唯一索引冲突 | 注销后无法注册新号 | 恢复流程覆盖，文档写明限制 |
| 验证码被刷 | 邮件成本与骚扰 | 场景隔离、账号/IP 双维度限频、失败计数 |
| 公网暴露被扫描 | 撞库、垃圾注册 | 限流、锁定、弱密码校验、HTTPS、管理接口独立鉴权 |
| 虚拟线程被误认为扩容手段 | 架构误判 | 文档明确其定位；热路径避免 `synchronized` |
| 前端范围膨胀 | 交付延期 | 只做三个页面，不做组件库自研与复杂动效 |
| 敏感配置泄露 | 安全事故 | 本地配置隔离、环境变量注入、示例文件不含真实值 |
| 范围膨胀 | 交付延期 | 非目标清单锁定，新增需求进入演进路线 |

## 13. 演进路线

| 方向 | 前置条件 | 方式与改造量 |
|---|---|---|
| OAuth2 / OIDC、真 SSO | 出现第三方接入或跨站免登需求 | 令牌签发逻辑保留；新增授权码端点、授权页、客户端注册与同意页，改造量约一个中等模块 |
| RBAC、项目级权限 | 角色超过两种或出现项目隔离需求 | 新增角色/权限/关联表，`isAdmin()` 改为权限校验 |
| 短信验证码 | 具备短信服务与预算 | 新增 Provider 实现，业务代码不改 |
| 邮箱验证码登录（passwordless，首次登录自动开户） | 出现低敏感消费级用户，或希望免密码登录；需先补 refresh token/长会话与更严格的登录限流 | 新增 `login` 验证码场景与登录接口；自动开户固定 `role=USER`，已注销走恢复、已封禁拒绝；管理员保留密码 + MFA；需评估邮件可用性、发信成本与一次性邮箱滥用 |
| JWT 密钥轮换（`kid` + 多密钥） | 有密钥泄漏风险或多服务共享验签 | 令牌头增加 `kid`，配置支持多密钥，旧密钥保留至令牌过期 |
| 鉴权状态缓存 | 压测证明数据库查询成为瓶颈 | 引入 Redis 或本地缓存，配套「提交后删缓存 + TTL 兜底」 |
| 首次登录强制改密 | 管理员重置密码成为高频操作 | 增加 `must_change_password` 状态位与登录后拦截 |
| 数据归档与物理清理 | 合规要求或数据量增长 | 定时任务归档至冷表，释放唯一标识 |
| 单端登出、审计日志 | 有合规或多端管理需求 | 引入 `jti` 黑名单、审计表 |
| 微服务拆分 | 出现独立的调用边界或部署频率差异 | 用户/认证能力独立部署，网关统一鉴权 |

**邮箱验证码登录候选说明（未排期）**：以邮箱所有权作为认证因子，首次登录自动开户，免去密码创建与找回流程，也消除密码撞库风险；代价是登录强依赖邮件可用性、每次登录产生发信成本，且验证码可被实时钓鱼转发。若启用，建议普通用户可选、管理员保留密码 + MFA，并先完成 refresh token/长会话、登录失败锁定与账号/IP 限流。已注销邮箱不得静默重新开户，已封禁账号必须拒绝。

## 附录 A：代码质量与规范

### A.1 分层约定

| 层次 | 约束 |
|---|---|
| controller | 只做参数校验、调用 service、组装返回；不写业务分支 |
| service | 业务规则与事务边界；对外暴露接口，实现类不加事务以外的横切逻辑 |
| mapper | 只做数据访问；不写业务判断 |
| model / dto | 实体不直接返回前端；入参与出参分别用 Param / DTO |

### A.2 编码约定

- 命名：类 `UpperCamel`、方法/变量 `lowerCamel`、常量 `UPPER_SNAKE`、包全小写；布尔值用 `is/has/can` 前缀
- 状态、角色、错误码使用枚举，禁止魔法值
- 业务异常统一抛 `BusinessException`，由全局异常处理转换；不吞异常、不返回 null 表示失败
- 入参使用 `@Valid` + 注解校验；事务放在 service 层
- 日志使用 SLF4J 占位符；不记录密码、令牌、验证码、完整邮箱
- SQL 关键字小写，参数绑定，禁止 `select *`，分页必须有上限
- 注释只解释「为什么」，不复述「做了什么」；公开接口写清参数、返回与异常

### A.3 工具与执行方式

| 工具 | 作用 | 执行时机 |
|---|---|---|
| Spotless（google-java-format） | 格式化 Java 代码 | 本地 `mvnw spotless:apply`；提交前检查 |
| Checkstyle（精简规则） | 命名、import、空行、Javadoc 等风格问题 | `mvnw checkstyle:check`；CI 强制 |
| SpotBugs（可选） | 空指针、错误的 equals、资源泄漏等字节码问题 | `mvnw spotbugs:check` |
| ESLint + Prettier | 前端代码检查与格式化 | `npm run lint` / `npm run format`；CI 强制 |
| pre-commit 钩子 | 提交前检查格式，失败时提示修复命令 | `git commit` 前；可 `--no-verify` 跳过 |
| GitHub Actions | 编译、单元测试、集成测试、静态检查 | 每次 push / pull request |

规则来源参考《阿里巴巴 Java 开发手册》与 Google Java Style；团队无需全量照搬，原则是「机器能查的交给机器，人只讨论机器判断不了的部分」。

### A.4 提交约定

采用 Conventional Commits：

| 前缀 | 用途 |
|---|---|
| `feat:` | 新功能 |
| `fix:` | 修复缺陷 |
| `docs:` | 文档 |
| `refactor:` | 不改变行为的重构 |
| `test:` | 测试 |
| `chore:` | 构建、依赖、工具 |

---

## 14. 参考

- Spring Boot 4.1 Reference Documentation
- Spring Security 7 Reference Documentation
- MyBatis-Plus 3.5.x Documentation
- springdoc-openapi 3.x Documentation：https://springdoc.org/
- MySQL 8.4 Reference Manual
- k6 Documentation：https://k6.io/docs/
- OWASP Application Security Verification Standard
