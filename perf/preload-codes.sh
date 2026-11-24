#!/usr/bin/env bash
# 把注册 / 忘记密码要用的验证码直接写进 Redis，绕过 /api/v1/auth/email/code
# 的「单 IP 每日 50 次」限制。压测机只有一个出口 IP，不预置就压不了这两类流程。
#
# 必须在仓库根目录执行（需要 docker-compose.yml 与运行中的 redis 容器）。
#
# 用法：
#   ./perf/preload-codes.sh <RUN_ID>
# RUN_ID 必须与 k6 的 -e K6_RUN_ID 一致，否则注册场景拿不到码。
#
# 账号映射与 perf/auth-load.js 必须保持一致：
#   忘记密码：perf{80001 + (vu-1)*200 + iter}@perf.local
#   注册：    reg-{RUN_ID}-vu{vu}-it{iter}@perf.local
# 默认 MAX_VUS=80、STRIDE=200，正好覆盖忘记密码段的 16000 个账号。

set -euo pipefail

RUN_ID=${1:?用法: ./perf/preload-codes.sh <RUN_ID>}
# RESP 里的长度按字节算，RUN_ID 只允许 ASCII 字母数字与 - _
[[ "$RUN_ID" =~ ^[A-Za-z0-9_-]+$ ]] || {
  echo "RUN_ID 只能包含字母、数字、- 和 _" >&2
  exit 1
}

CODE=${K6_CODE:-123456}
TTL=${K6_CODE_TTL:-7200}
MAX_VUS=${MAX_VUS:-80}
STRIDE=${STRIDE:-200}
RESET_START=${RESET_START:-80001}

# 直接按 RESP 协议输出，redis-cli --pipe 只认这个格式最稳
resp_set() {
  local key=$1
  printf '*5\r\n$3\r\nSET\r\n$%d\r\n%s\r\n$%d\r\n%s\r\n$2\r\nEX\r\n$%d\r\n%s\r\n' \
    "${#key}" "$key" "${#CODE}" "$CODE" "${#TTL}" "$TTL"
}

{
  for ((vu = 1; vu <= MAX_VUS; vu++)); do
    offset=$(( (vu - 1) * STRIDE ))
    for ((i = 0; i < STRIDE; i++)); do
      printf -v padded '%06d' "$((RESET_START + offset + i))"
      resp_set "verify:reset:perf${padded}@perf.local"
      resp_set "verify:register:reg-${RUN_ID}-vu${vu}-it${i}@perf.local"
    done
  done
} | docker compose exec -T redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning --pipe'

echo "预置完成：RUN_ID=${RUN_ID}，每类 $((MAX_VUS * STRIDE)) 个验证码，TTL=${TTL}s"
