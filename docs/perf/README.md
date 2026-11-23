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

## 结果

待部署后补：日期、环境、并发、成功率、P95/P99、是否触发锁定。
