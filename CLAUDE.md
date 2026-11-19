# user-center 项目规则

## 项目定位

单体统一认证服务 + 配套 Web 前端：为多个自有项目提供统一账号、登录与管理员用户管理，后续可演进为 OAuth2/SSO 与微服务。设计文档见 `docs/plan.md`，当前进度见 `docs/progress.md`。

## 目标目录结构

```
src/main/java/com/wwp/usercenter/
├── UserCenterApplication.java
├── common/          # ProblemDetail、ErrorCode、BusinessException、GlobalExceptionHandler
├── config/          # Security、Redis、Jackson、OpenAPI、异步与配置属性
├── controller/      # 接口层，只做校验与调用
├── service/         # 业务接口（含 EmailDispatchService）
│   └── impl/        # 业务实现，事务边界
├── mapper/          # MyBatis-Plus Mapper
├── model/
│   ├── entity/      # 数据库实体
│   ├── dto/         # 出参
│   ├── param/       # 入参
│   └── enums/       # 状态、角色、错误码枚举
├── security/        # JWT、过滤器、UserDetails
└── util/            # 无状态工具类

src/main/resources/
├── application.yaml / application-dev.yaml / application-local.yaml
├── mapper/          # MyBatis XML
└── db/
    ├── migration/   # Flyway 版本化迁移（V1__init.sql …）
    └── dev/         # 开发环境可重复迁移（种子管理员）

src/test/java/...    # 与主代码同包结构
deploy/nginx/        # Nginx 配置与 TLS 证书
.github/             # Dependabot、GitHub Actions CI
web/                 # Vue 3 前端
docs/                # 分层文档
perf/                # k6 压测脚本
```

## 硬性规范（细节见 docs/standards.md）

- controller 不写业务分支；事务放在 service；mapper 只做数据访问
- 实体不直接返回前端；入参 `param`、出参 `dto` 分开
- 状态、角色、错误码必须用枚举，禁止魔法值
- 业务失败统一抛 `BusinessException`，由全局异常处理转换为 RFC 9457 `ProblemDetail`；不吞异常
- 入参使用 `@Valid` + 注解校验
- 日志用 SLF4J 占位符；不打印密码、令牌、验证码、完整邮箱
- SQL 关键字小写、参数绑定、禁止 `select *`、分页必须有上限
- 表结构变更用 Flyway：只加新版本，不修改已执行脚本；开发种子放 `db/dev`
- 邮件通过 `EmailDispatchService` 异步投递，业务层不同步调用 `EmailSender`
- 测试入口 `./mvnw verify`；集成测试由 Failsafe 执行，本机无 Docker 时跳过
- 提交信息使用 Conventional Commits（`feat:` / `fix:` / `docs:` / `refactor:` / `test:` / `chore:`）

## 文档读取顺序（渐进式加载）

1. **每次会话开始**先读 `docs/progress.md`（当前状态、下一步、阻塞项）
2. **写代码前**读 `docs/standards.md`
3. **改设计或实现功能前**只读 `docs/plan.md` 中相关章节：先看标题，按章节定位，不要整篇加载
4. **需要历史取舍**时再读 `docs/decisions.md`（本地文件，不提交 GitHub）

禁止一次性加载全部文档；与当前任务无关的文档不读。

## 上下文压缩

- 会话结束前更新 `docs/progress.md`：只保留状态、下一步、阻塞项，控制在 80 行以内
- 过程性内容不落盘；决策结论写入 `docs/decisions.md`，最终设计写入 `docs/plan.md`
- 长输出先给结论；不要把整个文件内容贴回对话
- 开始工作前先看 `git status` 与最近提交，避免重复已完成的工作

## 安全与协作

- 禁止提交数据库密码、JWT 密钥、SMTP 授权码、虚拟机密码
- 敏感配置只放 `application-local.yaml`（已 gitignore）或环境变量
- 不引入计划外依赖；新增依赖必须先说明理由与替代方案
- 改动设计先更新文档，再动代码
- 本文件是项目级规则，优先级高于个人习惯；与用户最新指令冲突时以用户为准

## 当前状态

进度、环境与下一步以 `docs/progress.md` 为唯一来源，本文件不重复维护。
