import request from './request'

// 活动（api.md §5）与活动报名（api.md §6）
export const createActivity = (data) => request.post('/activities', data)
export const listActivities = (params) => request.get('/activities', { params })
export const myActivities = (params) => request.get('/activities/mine', { params })
export const getActivity = (id) => request.get(`/activities/${id}`)
export const cancelActivity = (id) => request.post(`/activities/${id}/cancel`)

export const registerActivity = (id) => request.post(`/activities/${id}/registration`)
export const cancelRegistration = (id) => request.delete(`/activities/${id}/registration`)
export const listMembers = (id, params) => request.get(`/activities/${id}/registrations`, { params })
export const myRegistrations = (params) => request.get('/registrations/mine', { params })
