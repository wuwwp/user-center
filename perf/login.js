import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080'
const EMAIL = __ENV.K6_EMAIL
const PASSWORD = __ENV.K6_PASSWORD
const RUN_ID = __ENV.K6_RUN_ID || String(Date.now())
const WRONG_EMAIL = __ENV.K6_WRONG_EMAIL || `k6-wrong-${RUN_ID}@example.com`
const LOCK_EMAIL = __ENV.K6_LOCK_EMAIL || `k6-lock-${RUN_ID}@example.com`

if (!EMAIL || !PASSWORD) {
  throw new Error('需要设置 K6_EMAIL 与 K6_PASSWORD（一个已注册且状态正常的测试账号）')
}

export const options = {
  scenarios: {
    login_success: {
      executor: 'constant-vus',
      vus: 2,
      duration: '30s',
      exec: 'loginSuccess',
    },
    login_wrong_password: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 3,
      startTime: '32s',
      exec: 'loginWrongPassword',
    },
    login_lockout: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      startTime: '36s',
      exec: 'loginLockout',
    },
  },
  thresholds: {
    'http_req_failed{scenario:login_success}': ['rate<0.01'],
    'http_req_duration{scenario:login_success}': ['p(95)<800'],
  },
}

const headers = { 'Content-Type': 'application/json' }

function postLogin(email, password) {
  return http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email, password }), {
    headers,
    tags: { name: 'POST /auth/login' },
  })
}

export function loginSuccess() {
  const response = postLogin(EMAIL, PASSWORD)
  check(response, {
    'login returns 200': (result) => result.status === 200,
    'login returns token': (result) => Boolean(result.json('token')),
  })
  sleep(1)
}

export function loginWrongPassword() {
  const response = postLogin(WRONG_EMAIL, 'WrongPassword123')
  check(response, {
    'wrong password returns 401': (result) => result.status === 401,
    'wrong password code is 40101': (result) => result.json('code') === 40101,
  })
}

export function loginLockout() {
  let locked = false
  for (let attempt = 0; attempt < 6; attempt += 1) {
    const response = postLogin(LOCK_EMAIL, 'WrongPassword123')
    if (response.json('code') === 40102) {
      locked = true
    }
    sleep(0.2)
  }
  check(locked, { 'sixth failure triggers 40102': (value) => value === true })
}
