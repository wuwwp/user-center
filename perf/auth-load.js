// 认证流程并发压测：登录 / 注册 / 忘记密码 / 修改密码。
//
// 与 perf/login.js 的分工：login.js 是冒烟 + 负向回归（单账号、低并发），
// 本脚本是一次只压一个正向流程、阶梯加压找饱和点的容量测试。
//
// 前置条件（详见 docs/perf/README.md）：
//   1. 已执行 perf/seed-users.sql 造出 10 万假用户
//   2. 注册 / 忘记密码要先执行 perf/preload-codes.sh <RUN_ID> 预置验证码
//
// 用法：
//   k6 run -e K6_BASE_URL=https://your-host -e K6_SCENARIO=login perf/auth-load.js
//   k6 run -e K6_BASE_URL=https://your-host -e K6_SCENARIO=reset \
//          -e K6_RUN_ID=<与预置码相同的 ID> perf/auth-load.js

import http from 'k6/http'
import exec from 'k6/execution'
import { check } from 'k6'

const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080'
const SCENARIO = (__ENV.K6_SCENARIO || 'login').toLowerCase()
const SEED_PASSWORD = __ENV.K6_SEED_PASSWORD || 'PerfSeed123456'
const NEW_PASSWORD = __ENV.K6_NEW_PASSWORD || 'PerfNew123456'
const CODE = __ENV.K6_CODE || '123456'
const RUN_ID = __ENV.K6_RUN_ID || String(Date.now())

// 与 perf/seed-users.sql 的分段约定保持一致
const SEGMENT_START = {
  login: 1,
  change: 60001,
  reset: 80001,
}

// 每个 VU 独占 STRIDE 个账号，避免并发抢同一行（last_login_time 更新会争行锁）。
// MAX_VUS * STRIDE 必须不超过被修改的那一段账号数（忘记密码段只有 16000）。
const STRIDE = 200
const MAX_VUS = 80

// 每个档位 30 秒；登录是 BCrypt 校验，注册/改密/重置还要多一次 BCrypt 编码，档位更低
const TIER_SECONDS = 30
const RATE_PROFILES = {
  login: [20, 50, 100],
  register: [10, 20, 40],
  reset: [10, 20, 40],
  change: [10, 20, 40],
}

const NAMES = {
  login: 'auth_login',
  register: 'auth_register',
  reset: 'auth_reset',
  change: 'user_change_password',
}

if (!(SCENARIO in RATE_PROFILES)) {
  throw new Error(
    `K6_SCENARIO 必须是 ${Object.keys(RATE_PROFILES).join(' / ')} 之一，当前为 ${SCENARIO}`,
  )
}

const RATES = RATE_PROFILES[SCENARIO]
const START_MS = Date.now()

export const options = {
  scenarios: {
    main: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      // 预分配等于上限，避免压测中途新建 VU 导致档位统计错位
      preAllocatedVUs: MAX_VUS,
      maxVUs: MAX_VUS,
      stages: RATES.map((rate) => ({ target: rate, duration: `${TIER_SECONDS}s` })),
      exec: 'run',
    },
  },
  thresholds: buildThresholds(),
}

const JSON_HEADERS = { 'Content-Type': 'application/json' }

/** 当前处于第几档（1 起）；模块初始化即测试开始，所有 VU 都预分配，误差可忽略。 */
function tierOf() {
  const elapsed = Date.now() - START_MS
  const tier = Math.floor(elapsed / (TIER_SECONDS * 1000)) + 1
  return Math.min(RATES.length, Math.max(1, tier))
}

/** 改密 / 重置：账号只能被消费一次，超出 STRIDE 说明加压时间配长了。 */
function exclusiveIndex(start) {
  if (__ITER >= STRIDE) {
    exec.test.abort(`每个 VU 最多迭代 ${STRIDE} 次，已超出；请缩短加压时间或调大 STRIDE`)
  }
  return start + (__VU - 1) * STRIDE + __ITER
}

/** 登录：账号可反复登录，超出后回绕到段内其它账号。 */
function rotatingIndex(start, size) {
  return start + (((__VU - 1) * STRIDE + __ITER) % size)
}

function loginEmail() {
  return toEmail(rotatingIndex(SEGMENT_START.login, 60000))
}

function exclusiveEmail(start) {
  return toEmail(exclusiveIndex(start))
}

function toEmail(index) {
  return `perf${String(index).padStart(6, '0')}@perf.local`
}

function registerEmail() {
  if (__ITER >= STRIDE) {
    exec.test.abort(`每个 VU 最多迭代 ${STRIDE} 次，已超出；请缩短加压时间或调大 STRIDE`)
  }
  return `reg-${RUN_ID}-vu${__VU}-it${__ITER}@perf.local`
}

function buildThresholds() {
  const thresholds = {
    [`http_req_failed{name:${NAMES[SCENARIO]}}`]: ['rate<0.01'],
  }
  if (SCENARIO === 'change') {
    // 改密场景额外包含一次登录，单独统计，避免登录失败被误记成改密失败
    thresholds[`http_req_failed{name:${NAMES.login}}`] = ['rate<0.01']
  }
  // 即使不设硬门槛，也要按档位声明一次，k6 才会在结果里分档输出 P95
  for (let tier = 1; tier <= RATES.length; tier += 1) {
    const limit = SCENARIO === 'login' ? 1000 : 5000
    thresholds[`http_req_duration{name:${NAMES[SCENARIO]},tier:${tier}}`] = [`p(95)<${limit}`]
  }
  return thresholds
}

export function run() {
  const tier = String(tierOf())
  switch (SCENARIO) {
    case 'register':
      return registerUser(tier)
    case 'reset':
      return resetPassword(tier)
    case 'change':
      return changePassword(tier)
    default:
      return loginUser(tier)
  }
}

function loginUser(tier) {
  const response = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: loginEmail(), password: SEED_PASSWORD }),
    { headers: JSON_HEADERS, tags: { name: NAMES.login, tier } },
  )
  check(response, {
    'login 200': (result) => result.status === 200,
    'login has token': (result) => result.body.includes('"token"'),
  })
  return response
}

function registerUser(tier) {
  const response = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ email: registerEmail(), code: CODE, password: NEW_PASSWORD }),
    { headers: JSON_HEADERS, tags: { name: NAMES.register, tier } },
  )
  check(response, {
    'register 200': (result) => result.status === 200,
    'register has userAccount': (result) => result.body.includes('userAccount'),
  })
  return response
}

function resetPassword(tier) {
  const response = http.post(
    `${BASE_URL}/api/v1/auth/password/reset`,
    JSON.stringify({
      email: exclusiveEmail(SEGMENT_START.reset),
      code: CODE,
      newPassword: NEW_PASSWORD,
    }),
    { headers: JSON_HEADERS, tags: { name: NAMES.reset, tier } },
  )
  check(response, {
    'reset 200': (result) => result.status === 200,
  })
  return response
}

function changePassword(tier) {
  const email = exclusiveEmail(SEGMENT_START.change)
  const loginResponse = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password: SEED_PASSWORD }),
    { headers: JSON_HEADERS, tags: { name: NAMES.login, tier } },
  )
  const loggedIn = check(loginResponse, {
    'change: login 200': (result) => result.status === 200,
  })
  if (!loggedIn) {
    return loginResponse
  }

  const token = JSON.parse(loginResponse.body).token
  const response = http.put(
    `${BASE_URL}/api/v1/users/me/password`,
    JSON.stringify({ oldPassword: SEED_PASSWORD, newPassword: NEW_PASSWORD }),
    {
      headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` },
      tags: { name: NAMES.change, tier },
    },
  )
  check(response, {
    'change password 200': (result) => result.status === 200,
  })
  return response
}
