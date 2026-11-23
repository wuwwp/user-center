# 登录压测

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
