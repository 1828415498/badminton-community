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

/**
 * 给拦截器抛出的错误打标记。
 *
 * 目的：页面里没写 catch 时，这个拒绝会升级成「未处理的 Promise 拒绝」，
 * 在控制台变成一条红色错误。但用户其实已经看到 ElMessage 提示了 ——
 * 那不是程序 bug，是"页面没接住"。
 * main.js 里的 unhandledrejection 监听只吞掉带这个标记的错误，
 * 真正的 JS 异常（TypeError 等）照旧暴露出来。
 */
function toastError(message) {
  ElMessage.error(message)
  const err = new Error(message)
  err.__toasted = true
  return err
}

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
    return Promise.reject(toastError((body && body.message) || '操作失败'))
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
      ElMessage.error(
        (error.response && error.response.data && error.response.data.message) ||
          error.message ||
          '网络异常'
      )
    }
    // 标记：已经提示过用户，只是没被页面接住
    error.__toasted = true
    return Promise.reject(error)
  }
)

export default request
