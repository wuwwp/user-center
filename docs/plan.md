# 用户中心（统一认证服务）设计文档

## 文档信息

| 项目 | 内容 |
|---|---|
| 文档名称 | 用户中心（统一认证服务）设计文档 |
| 文档版本 | v0.1 |
| 文档状态 | 评审中 |
| 编写日期 | 2026-11-12 |
| 适用范围 | user-center 服务的设计、开发、测试与部署 |
| 密级 | 公开 |

## 修订记录

| 版本 | 日期 | 修订人 | 修订说明 |
|---|---|---|---|
| v0.1 | 2026-11-12 | — | 初稿：确认架构、认证方案、数据模型、接口与实施阶段 |

## 1. 引言

### 1.1 目的

本文档定义用户中心服务的目标、范围、架构、数据模型、接口、安全要求、实施计划与验收标准，作为开发、测试和部署的依据。

### 1.2 术语

| 术语 | 说明 |
|---|---|
| 认证中心 | 统一提供注册、登录、令牌签发与校验的独立服务 |
| 访问令牌 | 登录成功后签发的 JWT，客户端以 Bearer 方式携带 |
| 逻辑删除 | 通过 `is_delete` 标记注销，数据保留 |
| 恢复 | 已注销账号在验证身份后重新启用 |
| 封禁 | 管理员对账号的禁用状态，与注销相互独立 |
| 用户枚举 | 通过接口响应差异推断某邮箱/账号是否存在 |

## 2. 范围

### 2.1 目标

1. 为多个自有 Web 项目提供统一账号与登录能力
2. 提供管理员对用户的管理能力（查询、封禁/解封、重置密码）
3. 以独立单体服务交付，鉴权无状态，可平滑演进为 OAuth2/SSO 与微服务
4. 工程实践完整：分层、统一返回、异常处理、参数校验、接口文档、自动化测试、容器化部署

### 2.2 非目标

- 图形验证码、第三方登录、OAuth2/OIDC 授权服务器
- RBAC 细粒度权限、多租户、项目级权限
- 短信验证码（保留 Provider 接口，不实现）
- 头像上传、邮箱/手机号换绑
- 审计日志、单端登出
- 微服务拆分、注册中心、网关

## 3. 总体设计

### 3.1 架构

```
Web 项目 A ──┐
Web 项目 B ──┼──HTTPS──> Nginx ──> user-center（单体服务）
小程序（后续）┘                        ├── MySQL 8.0（用户数据）
                                      └── Redis 7.x（验证码、限流、令牌版本）
```

- 服务无状态，可水平扩展
- 客户端各自实现登录页，调用认证接口获取 JWT
- 管理员通过管理接口操作用户，管理接口独立鉴权

### 3.2 技术选型

| 领域 | 选型 | 说明 |
|---|---|---|
| 运行时 | Java 21（LTS） | 启用虚拟线程 |
| 框架 | Spring Boot 4.1.x / Spring MVC | 与现有工程基线一致 |
| 安全 | Spring Security 7 + JWT（jjwt） | 标准鉴权链，便于后续接入 OAuth2 |
| 持久层 | MyBatis-Plus 3.5.18 + MySQL 8.0 | 参数绑定，无字符串拼接 SQL |
| 缓存 | Redis 7.2+ | 验证码、限流、失败计数、令牌版本 |
| 参数校验 | spring-boot-starter-validation | 注解校验 + 全局异常转换 |
| 邮件 | spring-boot-starter-mail | SMTP 可配置，开发环境可关闭 |
| 接口文档 | springdoc-openapi 3.2.0 | OpenAPI 3 + Swagger UI |
| 测试 | JUnit 5、Mockito、MockMvc、Testcontainers | 单元 + 接口 + 集成 |
| 部署 | Docker / Docker Compose | 应用、MySQL、Redis 一键启动 |

**虚拟线程说明**：`spring.threads.virtual.enabled=true`，由 Spring Boot 自动配置 Tomcat 与 `@Async` 执行器。本服务为 I/O 等待型（数据库、Redis、SMTP），虚拟线程可降低线程占用；数据库并发仍受连接池上限约束，虚拟线程不是扩容手段，仅减少线程调度开销。

## 4. 功能需求

| 编号 | 功能 | 说明 | 优先级 |
|---|---|---|---|
| FR-01 | 邮箱注册 | 邮箱验证码校验，自动分配用户名，密码 BCrypt 存储 | 高 |
| FR-02 | 登录 | 邮箱 + 密码，签发 JWT | 高 |
| FR-03 | 登出 | 令牌版本自增，当前用户所有令牌失效 | 高 |
| FR-04 | 当前用户 | 返回脱敏后的用户信息 | 高 |
| FR-05 | 修改密码 | 校验旧密码，成功后所有令牌失效 | 高 |
| FR-06 | 忘记密码 | 邮箱验证码重置密码 | 高 |
| FR-07 | 注销 | 密码确认，逻辑删除，令牌失效，管理员不可自助注销 | 中 |
| FR-08 | 恢复 | 原密码或邮箱验证码验证后恢复账号 | 中 |
| FR-09 | 管理员查询 | 按用户名/邮箱/手机号/状态分页查询 | 高 |
| FR-10 | 管理员封禁/解封 | 封禁后旧令牌失效，登录被拒 | 高 |
| FR-11 | 管理员重置密码 | 生成随机密码一次性返回，不记录日志 | 高 |
| FR-12 | 验证码发送 | 限频发送，开发环境输出到日志 | 高 |

## 5. 非功能需求

| 编号 | 类别 | 要求 |
|---|---|---|
| NFR-01 | 安全 | 全站 HTTPS；密码不可逆存储；令牌可吊销；防用户枚举；管理接口独立鉴权 |
| NFR-02 | 性能 | 单实例常规接口 P95 < 200ms；分页接口单页上限 100 条 |
| NFR-03 | 可用性 | 服务无状态，可多实例部署；MySQL/Redis 故障时返回明确错误，不泄露内部细节 |
| NFR-04 | 可维护性 | 分层清晰；统一返回与错误码；配置与代码分离；敏感配置不进仓库 |
| NFR-05 | 可观测性 | 结构化日志，敏感字段脱敏；关键操作记录用户 ID 与结果 |
| NFR-06 | 兼容性 | API 带版本前缀 `/api/v1`，破坏性变更升版本 |

## 6. 数据设计

### 6.1 用户表

```sql
create table `user` (
    id            bigint unsigned auto_increment primary key,
    user_account  varchar(32)  not null comment '对外标识，自动生成，不参与登录',
    nickname      varchar(32)  null     comment '昵称',
    email         varchar(128) not null comment '登录标识',
    phone         varchar(20)  null     comment '预留，本期不参与登录',
    password_hash varchar(100) not null comment 'BCrypt',
    avatar_url    varchar(512) null,
    gender        tinyint      null,
    role          tinyint      not null default 0 comment '0-普通用户 1-管理员',
    status        tinyint      not null default 0 comment '0-正常 1-封禁',
    is_delete     tinyint      not null default 0 comment '0-未注销 1-已注销',
    token_version int          not null default 0,
    create_time   datetime     not null default current_timestamp,
    update_time   datetime     not null default current_timestamp on update current_timestamp,
    unique key uk_email (email),
    unique key uk_user_account (user_account),
    unique key uk_phone (phone)
) engine = innodb default charset = utf8mb4;
```

### 6.2 设计说明

- `id` 为内部代理键，不对外暴露；对外使用 `user_account`
- `email` 为登录标识，唯一；`user_account` 自动生成，唯一，作为展示与检索标识
- 注销为逻辑删除，保留数据以支持恢复；唯一索引不释放标识，注销后同邮箱注册引导至恢复流程
- `token_version` 与 JWT 中的 `ver` 比对，实现改密、封禁、注销、恢复后旧令牌立即失效
- 角色变更同样自增 `token_version`，避免令牌中的旧角色继续生效

### 6.3 数据生命周期

| 阶段 | 状态 | 数据 |
|---|---|---|
| 注册 | `is_delete=0` | 全部保留 |
| 注销 | `is_delete=1` | 保留，可恢复 |
| 封禁 | `status=1` | 保留，不可登录，不可自助恢复 |
| 恢复 | `is_delete=0` | 保留原数据与角色 |

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
| 40100 | 401 | 未登录或令牌无效 |
| 40101 | 401 | 邮箱或密码错误 |
| 40102 | 401 | 登录失败次数过多，账号锁定 |
| 40300 | 403 | 无权限 |
| 40400 | 404 | 资源不存在 |
| 40900 | 409 | 资源冲突（邮箱已注册等） |
| 40901 | 409 | 账号已注销，可申请恢复 |
| 42900 | 429 | 请求过于频繁 |
| 50000 | 500 | 系统错误 |

### 7.3 接口清单

#### 认证

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| post | `/api/v1/auth/register` | 邮箱验证码注册 | 否 |
| post | `/api/v1/auth/login` | 邮箱 + 密码登录 | 否 |
| post | `/api/v1/auth/logout` | 登出 | 是 |
| post | `/api/v1/auth/email/code` | 发送注册/重置验证码 | 否 |

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
- claims 仅包含 `sub`(用户 ID)、`role`、`ver`(令牌版本)、`jti`、`iat`、`exp`
- 有效期 2 小时，不提供刷新令牌；过期后重新登录
- 每个请求校验签名、有效期、`ver` 与当前账号状态（封禁/注销）
- 作用域：令牌仅用于本服务；后续多服务独立验签时切换 RS256

### 8.2 账号与密码

- 密码 BCrypt（cost 10+），永不返回、不写日志
- 密码策略：8~64 位，至少包含字母与数字
- 注册、登录、发码响应统一文案，避免用户枚举
- 登录失败 5 次锁定 15 分钟；验证码同邮箱 60 秒限发、每日 10 次

### 8.3 权限

- 管理接口要求 `role=1`，按路径前缀统一配置
- 用户资源操作校验归属，防越权（IDOR）
- 角色变更自增 `token_version`，避免旧令牌保留管理员权限

### 8.4 数据与传输

- 接口返回 DTO，手机号仅管理员可见且掩码
- SQL 全部参数绑定，禁止字符串拼接
- 全站 HTTPS；CORS 白名单；安全响应头
- 无 Cookie 认证，不依赖 CSRF Token
- 敏感配置（数据库密码、JWT 密钥、SMTP 授权码）仅存本地/环境变量，已加入 `.gitignore`

### 8.5 日志

- 记录登录结果、封禁、重置密码等事件，包含操作者 ID 与目标 ID
- 不记录密码、令牌、验证码、邮箱完整地址（脱敏）

## 9. 环境与部署

### 9.1 环境

| 环境 | 用途 | 组件 |
|---|---|---|
| local | 本地开发 | 本机 MySQL 8.0、Redis 7.x |
| test | 自动化测试 | Testcontainers（MySQL 8.0 + Redis 7.x） |
| prod | 部署 | 云服务器 + Docker Compose |

### 9.2 部署拓扑

```
Nginx（HTTPS、反向代理）
  └── user-center（容器，Java 21）
        ├── MySQL 8.0（容器，数据卷持久化）
        └── Redis 7.x（容器，AOF 持久化）
```

### 9.3 交付物

- `Dockerfile`：多阶段构建，运行镜像使用 JRE 21
- `docker-compose.yml`：应用、MySQL、Redis 编排，配置通过环境变量注入
- `.env.example`：完整配置项模板，不含真实凭据
- 部署说明：初始化 SQL、启动步骤、健康检查

## 10. 测试策略

| 层级 | 工具 | 覆盖内容 |
|---|---|---|
| 单元测试 | JUnit 5 + Mockito | 注册冲突、锁定、恢复条件、权限判断等业务规则 |
| Web 层测试 | MockMvc | 参数校验、错误码、401/403 鉴权矩阵 |
| 集成测试 | Spring Boot Test + Testcontainers | 真实 MySQL/Redis 下的注册、登录、改密、注销恢复全链路 |
| 接口验收 | Swagger / OpenAPI + 测试用例表 | 手工验收与回归 |

要求：核心业务分支覆盖率不低于 70%；安全相关路径（越权、封禁、令牌失效）必须有测试。

## 11. 实施计划

| 阶段 | 内容 | 交付物 | 验收标准 |
|---|---|---|---|
| 阶段 0 工程基础 | 依赖、配置分层、统一返回/异常/校验、表结构与初始化脚本、容器编排骨架 | 可启动工程、`init.sql` | 启动成功；未认证访问返回 401 |
| 阶段 1 账号核心 | 注册、登录、登出、当前用户、修改密码、邮箱验证码重置 | 认证接口与单元测试 | 注册→登录→改密→重置全流程通过 |
| 阶段 2 鉴权与防护 | Spring Security 链、JWT Filter、角色校验、Redis 限流与锁定、令牌版本 | 安全配置与鉴权测试 | 401/403 矩阵、封禁与改密后令牌失效通过 |
| 阶段 3 管理员 | 分页查询、封禁/解封、重置密码 | 管理接口与测试 | 管理员流程通过，普通用户不可访问 |
| 阶段 4 注销与恢复 | 注销、恢复流程、状态校验 | 完整闭环与测试 | 注销→登录提示→恢复→旧令牌失效 |
| 阶段 5 收尾 | OpenAPI 导出、README、Dockerfile/compose、CI、验收 | 可部署产物与文档 | 新环境一条命令启动，测试全绿 |

## 12. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| Spring Security 7 / Boot 4 资料少 | 开发阻塞 | 以官方文档为准，安全配置集中管理 |
| 逻辑删除与唯一索引冲突 | 注销后无法注册新号 | 恢复流程覆盖，文档写明限制 |
| 验证码被刷 | 邮件成本与骚扰 | Redis 限频、失败计数、每日上限 |
| 虚拟线程被误认为扩容手段 | 架构误判 | 文档明确其定位，容量仍由连接池决定 |
| 敏感配置泄露 | 安全事故 | 本地配置隔离、环境变量注入、示例文件不含真实值 |
| 范围膨胀 | 交付延期 | 非目标清单锁定，新增需求进入演进路线 |

## 13. 演进路线

| 方向 | 前置条件 | 方式 |
|---|---|---|
| OAuth2 / OIDC、真 SSO | 出现第三方接入或跨站免登需求 | 令牌签发逻辑不动，增加授权码端点 |
| RBAC、项目级权限 | 角色超过两种或出现项目隔离需求 | 新增角色/权限/关联表，`isAdmin()` 改为权限校验 |
| 短信验证码 | 具备短信服务与预算 | 新增 Provider 实现，业务代码不改 |
| 微服务拆分 | 出现独立的调用边界或部署频率差异 | 用户/认证能力独立部署，网关统一鉴权 |
| 单端登出、审计日志 | 有合规或多端管理需求 | 引入 `jti` 黑名单、审计表 |

## 14. 参考

- Spring Boot 4.1 Reference Documentation
- Spring Security 7 Reference Documentation
- MyBatis-Plus 3.5.x Documentation
- springdoc-openapi 3.x Documentation：https://springdoc.org/
- OWASP Application Security Verification Standard
