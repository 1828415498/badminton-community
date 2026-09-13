import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * axios 实例：统一处理「认证头 + 响应解包 + 错误提示」。
 *
 * 后端约定（api.md 第 9/10 节）：
 *   - 业务成功: { code: "0", message: "success", data: ... }
 *   - 业务失败: { code: "SOME_CODE", message: "...", data: null }，HTTP 仍为 200
 *   - 401 表示未认证/凭据失效
 *
 * 因此拦截器里判断 code === "0" 才算成功，其余都当业务失败抛出，
 * 页面里用 try/catch 即可，不需要到处写 if (res.code === '0')。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截：自动带上 JWT
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：解包 data、统一报错、401 跳登录
request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === '0') {
      return body.data
    }
    const msg = (body && body.message) || '操作失败'
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
  (error) => {
    const status = error.response && error.response.status
    if (status === 401) {
      localStorage.removeItem('token')
      ElMessage.error('登录已失效，请重新登录')
      // 用 location 跳转，避免在拦截器里 import router 造成循环依赖
      if (location.hash !== '#/login') {
        location.hash = '#/login'
      }
    } else {
      const msg =
        (error.response && error.response.data && error.response.data.message) ||
        error.message ||
        '网络异常'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default request
