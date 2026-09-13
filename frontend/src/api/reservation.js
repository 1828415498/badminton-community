import request from './request'

// 个人球场预约（api.md §4）
export const createReservation = (data) => request.post('/reservations', data)
export const myReservations = (params) => request.get('/reservations/mine', { params })
export const getReservation = (id) => request.get(`/reservations/${id}`)
export const cancelReservation = (id) => request.post(`/reservations/${id}/cancel`)
