import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, getMe } from '@/api/auth'

/**
 * 当前用户状态。token 落地到 localStorage，刷新页面不丢；
 * user 每次进入受保护页面时通过 /users/me 拉取（保证资料是最新的）。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(null)

  async function login(username, password) {
    const data = await loginApi({ username, password })
    token.value = data.token
    user.value = data.user
    localStorage.setItem('token', data.token)
    return data
  }

  async function fetchMe() {
    if (!token.value) return null
    try {
      user.value = await getMe()
      return user.value
    } catch (e) {
      // token 失效：清干净，让路由守卫把用户送回登录页
      token.value = ''
      user.value = null
      localStorage.removeItem('token')
      return null
    }
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (e) {
      // 后端登出失败不影响前端清理（JWT 无状态，前端丢弃即可）
    }
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
  }

  return { token, user, login, fetchMe, logout }
})
