# 项目进度

更新日期：2026-11-13
设计文档版本：v0.4
当前阶段：阶段 0（工程基础）未开始

## 已完成

- 需求与设计讨论完成，`docs/plan.md` 定稿到 v0.4
- 决策记录 `docs/decisions.md`（本地文件，已 gitignore，不提交）
- Git 仓库初始化并推送：https://github.com/wuwwp/user-center （main）
- JDK 21 安装到 `D:\dev\jdk-21`；系统 `JAVA_HOME` 已切换到 21，系统 `PATH` 首位已加入 `D:\dev\jdk-21\bin`
  - 原系统环境备份：`D:\dev\env-backup\machine-path.txt`、`machine-java-home.txt`
  - 已打开的终端和 IDE 需重启才能读到新变量
- 生成 SSH 密钥并配置主机别名 `user-center-vm`（`~/.ssh/config`）
- Context7 MCP 已安装（user 作用域、匿名模式），新会话生效
- 已删除 IDE 生成文件：`.idea/`、`HELP.md`、`target/`

## 环境

- 本机：Windows 11、JDK 21、Node 24、MySQL 8.0.46（旧版，不用于本项目）
- 开发虚拟机：`ssh user-center-vm`（192.168.216.130，用户 wwp）
  - SSH 免密登录已验证：`ssh user-center-vm "docker --version"` → Docker 29.9.2
  - MySQL 8.4 与 Redis 7 容器尚未创建
- 部署：云服务器（已有，规格待确认；建议 2 核 4G）
- 数据库：项目统一使用 MySQL 8.4 LTS

## 下一步（按顺序）

1. 阶段 0：重写工程骨架
   - 删除旧 `User`、`UserService`、`UserServiceImpl`、`UserMapper`、`UserMapper.xml` 与旧测试
   - 加入依赖：security、redis、validation、mail、jjwt、actuator、springdoc 3.2.0、spotless、checkstyle、testcontainers
   - 配置分层：`application.yaml` / `application-dev.yaml` / `application-local.yaml`
   - 建 `common`（BaseResponse、ErrorCode、BusinessException、GlobalExceptionHandler）
   - 建 Security + JWT Filter 骨架
   - `src/main/resources/db/init.sql`：建表 + 种子管理员
   - Docker Compose：MySQL 8.4、Redis 7、Nginx、应用
2. 阶段 1：注册、登录、登出、当前用户、修改密码、邮箱验证码重置

## 待确认 / 阻塞

- SMTP 授权码未提供（QQ/163 邮箱），阶段 1 先用日志实现，不阻塞开发
- 云服务器规格未定，部署阶段前确认
- `pom.xml` 有未提交改动（`java.version=21`、`commons-lang3`），阶段 0 重写时统一整理

## 交接说明

- 新会话开始：先读本文件，再读 `CLAUDE.md` 的文档读取顺序
- 设计细节：`docs/plan.md` 按章节读
- 编码规范：`docs/standards.md`
- 历史取舍与面试准备：`docs/decisions.md`（本地，不提交）
- 不要在仓库写任何真实密码、密钥、授权码；本地配置放 `application-local.yaml`
