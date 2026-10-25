import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// 模拟 api 模块，隔离网络依赖（真实连通性由 E2E 脚本覆盖）
vi.mock('../src/api', () => ({
  apiLogin: vi.fn(async () => ({
    data: {
      code: 0,
      message: 'ok',
      data: {
        accessToken: 'token-abc',
        expiresIn: 7200,
        user: { id: 1, username: 'admin', nickname: '管理员', role: 'ADMIN', status: 1, createdAt: '' },
      },
    },
  })),
  apiLogout: vi.fn(async () => ({})),
}))

import { useAuthStore } from '../src/stores/auth'

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('初始状态未登录', () => {
    const auth = useAuthStore()
    expect(auth.isLoggedIn).toBe(false)
    expect(auth.isAdmin).toBe(false)
  })

  it('login 成功后写入 token 与用户信息并持久化', async () => {
    const auth = useAuthStore()
    await auth.login('admin', 'x')
    expect(auth.isLoggedIn).toBe(true)
    expect(auth.isAdmin).toBe(true)
    expect(auth.user?.username).toBe('admin')
    expect(localStorage.getItem('devpilot_token')).toBe('token-abc')
  })

  it('logout 清空登录态', async () => {
    const auth = useAuthStore()
    await auth.login('admin', 'x')
    await auth.logout()
    expect(auth.isLoggedIn).toBe(false)
    expect(localStorage.getItem('devpilot_token')).toBeNull()
  })
})
