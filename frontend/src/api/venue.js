import request from './request'

// 场馆与球场（api.md §3）
export const listVenues = () => request.get('/venues')
export const listCourts = (venueId, params) => request.get(`/venues/${venueId}/courts`, { params })
export const getOccupancy = (courtId, date) =>
  request.get(`/courts/${courtId}/occupancy`, { params: { date } })
