import { defineStore } from 'pinia'
import { apiLogin, apiLogout } from '../api'
import type { UserVO } from '../api/types'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('devpilot_token') || '',
    user: JSON.parse(localStorage.getItem('devpilot_user') || 'null') as UserVO | null,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN',
  },
  actions: {
    async login(username: string, password: string) {
      const resp = await apiLogin(username, password)
      const data = resp.data.data
      this.token = data.accessToken
      this.user = data.user
      localStorage.setItem('devpilot_token', data.accessToken)
      localStorage.setItem('devpilot_user', JSON.stringify(data.user))
    },
    async logout() {
      try {
        await apiLogout()
      } finally {
        this.token = ''
        this.user = null
        localStorage.removeItem('devpilot_token')
        localStorage.removeItem('devpilot_user')
      }
    },
  },
})
