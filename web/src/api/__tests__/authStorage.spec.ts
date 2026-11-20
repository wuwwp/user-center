import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearToken,
  getToken,
  isSafeInternalPath,
  setToken,
} from '../authStorage'

describe('authStorage', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('reads and writes the token in sessionStorage', () => {
    setToken('abc')
    expect(getToken()).toBe('abc')
    clearToken()
    expect(getToken()).toBeNull()
  })

  it('accepts only internal return paths', () => {
    expect(isSafeInternalPath('/profile')).toBe(true)
    expect(isSafeInternalPath('/admin?page=2')).toBe(true)
    expect(isSafeInternalPath('//evil.example.com')).toBe(false)
    expect(isSafeInternalPath('https://evil.example.com')).toBe(false)
    expect(isSafeInternalPath(undefined)).toBe(false)
  })
})
