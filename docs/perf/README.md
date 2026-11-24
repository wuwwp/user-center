# 压测

两套脚本，分工不同：

| 脚本 | 用途 | 并发 |
|---|---|---|
| `perf/login.js` | 冒烟 + 负向回归（错误密码、锁定） | 2 VU / 30s |
| `perf/auth-load.js` | 容量压测：登录 / 注册 / 忘记密码 / 修改密码 | 阶梯 0→20→50→100 rps |

## 一、冒烟与负向：`login.js`

脚本：`perf/login.js`。三个场景依次执行：

1. `login_success`：2 VU 持续 30 秒，校验 200 与 token，阈值失败率 < 1%、P95 < 800ms。
2. `login_wrong_password`：3 次错误密码，校验 401 / 40101。
3. `login_lockout`：同一随机邮箱连续 6 次失败，校验第 6 次返回 40102。

## 运行

```bash
k6 run \
  -e K6_BASE_URL=https://your-domain.example \
  -e K6_EMAIL=perf-user@example.com \
  -e K6_PASSWORD='perf-password' \
  perf/login.js
```

- 需要一个已注册且状态正常的测试账号；错误密码与锁定场景使用随机邮箱，不影响测试账号。
- 建议在生产同等规格（2 核 2G）上运行，并记录机器规格、并发、P95/P99。
- 运行结束把 k6 摘要保存到 `docs/perf/`，文件名带日期与环境。

## 结果（2026-11-23）

- 环境：阿里云 2 核 2G、Docker Compose、自签 TLS，k6 从内网虚拟机发起。
- 场景：成功登录 2 VU / 30s；错误密码 3 次；同邮箱连续 6 次失败。
- 成功场景：52 次请求，0 失败，平均 157ms，P95 174ms。
- 错误密码：3 次均返回 401 / 40101。
- 锁定：第 6 次返回 40102。
- 总计 61 次请求、38.1 秒；整体失败率 14.75% 来自预期的 401。
- 原始输出：`docs/perf/login-2026-11-23.txt`。

## 二、容量压测：`auth-load.js`

目标：这台机器每种认证操作最多能扛每秒多少次，P95 从哪一档开始拐弯。

### 数据与账号约定

10 万假用户由 `perf/seed-users.sql` 一次性造出，按用途分段，四个场景互不污染：

| 段 | 账号 | 用途 | 初始状态 |
|---|---|---|---|
| 1–60000 | `perf000001@perf.local` … | 登录 | 正常 |
| 60001–80000 | … | 修改密码 | 正常 |
| 80001–96000 | … | 忘记密码 | 正常 |
| 96001–98000 | … | 干扰数据 | 封禁 |
| 98001–100000 | … | 干扰数据 | 已注销 |

- 密码统一为 `PerfSeed123456`，全表复用同一个 BCrypt(cost=10) 哈希。
- 每个 VU 独占 200 个账号（`STRIDE`，最多 80 个 VU），避免并发更新同一行的 `last_login_time` 争锁。

### 步骤

在**云服务器仓库根目录**执行（需要 `docker-compose.yml` 与运行中的容器）：

```bash
# 1. 造数（约 10 万行，10 批插入）
docker compose exec -T mysql mysql -uroot -p"$DB_ROOT_PASSWORD" usercenter < perf/seed-users.sql

# 2. 预置验证码（注册与忘记密码需要；RUN_ID 自己定，两处要一致）
./perf/preload-codes.sh 20261124

# 3. 四类分别压，一次只跑一个
k6 run -e K6_BASE_URL=https://your-host -e K6_SCENARIO=login perf/auth-load.js
k6 run -e K6_BASE_URL=https://your-host -e K6_SCENARIO=register -e K6_RUN_ID=20261124 perf/auth-load.js
k6 run -e K6_BASE_URL=https://your-host -e K6_SCENARIO=reset -e K6_RUN_ID=20261124 perf/auth-load.js
k6 run -e K6_BASE_URL=https://your-host -e K6_SCENARIO=change -e K6_RUN_ID=20261124 perf/auth-load.js

# 4. 复原账号密码，便于重跑
docker compose exec -T mysql mysql -uroot -p"$DB_ROOT_PASSWORD" usercenter < perf/reset-scenario-accounts.sql

# 5. 全部结束，删掉压测数据
docker compose exec -T mysql mysql -uroot -p"$DB_ROOT_PASSWORD" usercenter < perf/cleanup-perf-data.sql
```

重跑注意：验证码是一次性的，`reset` 与 `register` 每次重跑都要重新执行第 2 步；`register` 的 RUN_ID 每次换新的，否则邮箱已存在会返回 409。

### 为什么要把验证码预置进 Redis

注册和忘记密码都必须带邮箱验证码，而发码接口限制「同一 IP 每天 50 次」。压测机只有一个出口 IP，不预置就只能发 50 个码，压不起来。
`preload-codes.sh` 直接把 `verify:register:*` / `verify:reset:*` 写进 Redis，压测时验证码恒为 `123456`，测的是注册 / 重置本身的数据库与 BCrypt 成本。

**未覆盖**：`POST /api/v1/auth/email/code` 的真实并发能力。它的瓶颈是有意设计的限频，不是机器容量；要测它就得改限频配置或换 IP，不在本轮范围。

### 解读

- 每档 30 秒，请求带 `tier` 标签；登录的四档 P95 直接看阈值输出。
- 注册 / 重置 / 改密没有设 P95 阈值，只记录：它们比登录多一次 BCrypt 编码，正常会先饱和。
- 改密场景包含一次登录 + 一次改密，两次请求分开打 tag，避免登录耗时混进改密数据。
- 锁规则只对失败计数生效，正向压测不会触发 40102；负向验证留给 `login.js`。
- 看 `dropped_iterations`：非 0 表示 80 个 VU 已经不够用（或服务端彻底跟不上），此时 P95 已失真，以更低的档位为准。

### 结果（2026-11-24）

| 场景 | 健康档位 | P95 | 塌陷档位 |
|---|---|---|---|
| 登录 | ≤20 rps | 288ms | 20→50 rps 起秒级 |
| 注册 | ≤20 rps | 184ms | 20→40 rps 起 3.6s |
| 忘记密码 | ≤20 rps | 284ms | 20→40 rps 起 3.9s |
| 修改密码 | ≤10 rps | 987ms | 10→20 rps 起 5.9s |

瓶颈是应用 CPU（BCrypt）先打满，随后 Hikari 默认 10 个连接被耗尽，超时 3 秒后返回 500；MySQL 全程 CPU < 10%。
把连接池调到 20 后 500 消失，但吞吐与 P95 不变——CPU 才是硬上限。
完整数据与归因见 `docs/perf/auth-load-2026-11-24.txt`。
