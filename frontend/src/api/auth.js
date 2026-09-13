import request from './request'

// 认证与个人资料（api.md §2）
export const register = (data) => request.post('/auth/register', data)
export const login = (data) => request.post('/auth/login', data)
export const logout = () => request.post('/auth/logout')
export const getMe = () => request.get('/users/me')
export const updateMe = (data) => request.put('/users/me', data)
