import request from './request'

// 个人球场预约（api.md §4）
export const createReservation = (data) => request.post('/reservations', data)
export const myReservations = (params) => request.get('/reservations/mine', { params })
export const getReservation = (id) => request.get(`/reservations/${id}`)
export const cancelReservation = (id) => request.post(`/reservations/${id}/cancel`)

// 预约凭证（报表打印用）：比 getReservation 多带场馆名、占用时长、来源与状态文本，
// 数据来自数据库视图 v_reservation_detail（见 sql/views.sql）
export const getReservationReceipt = (id) => request.get(`/reservations/${id}/receipt`)
