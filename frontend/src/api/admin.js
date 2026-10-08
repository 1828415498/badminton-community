import request from './request'

/**
 * 管理端接口（api.md §8）。
 *
 * <p>路径都在 /api/admin/* 下。拦截器只保证"已登录"，"是不是管理员"由后端
 * Service 层判定（认证与授权分离）—— 所以前端隐藏入口只是体验，不是安全边界。</p>
 *
 * ⚠ 后端全局配了 SNAKE_CASE（application.yml 的 property-naming-strategy），
 *    所以**请求体也要用下划线**：reject_reason / venue_id。写驼峰会 400。
 */

// ---- 活动审核（api.md §5.6–§5.9）----

/** 待审核队列，先创建先审 */
export const listPendingActivities = (params) =>
  request.get('/admin/activities/pending', { params })

/** 审核通过：场地与报名保持不变，活动转为「已发布」开放报名 */
export const approveActivity = (id) => request.post(`/admin/activities/${id}/approve`)

/** 审核驳回：必须给原因（会持久化到 reject_reason 并展示给发起人），同时释放场地 */
export const rejectActivity = (id, rejectReason) =>
  request.post(`/admin/activities/${id}/reject`, { reject_reason: rejectReason })

/** 管理员取消活动：不受"未开始"限制 */
export const adminCancelActivity = (id) => request.post(`/admin/activities/${id}/cancel`)

// ---- 球场维护（api.md §3.4–§3.7）----

/** 新增球场（默认 status=1 可预约） */
export const createCourt = (venueId, name) =>
  request.post('/admin/courts', { venue_id: venueId, name })

/** 修改球场名 */
export const renameCourt = (courtId, name) => request.put(`/admin/courts/${courtId}`, { name })

/** 停用：该场地有未来有效预约时后端会拒绝（完整性约束） */
export const disableCourt = (courtId) => request.put(`/admin/courts/${courtId}/disable`)

/** 恢复可预约 */
export const enableCourt = (courtId) => request.put(`/admin/courts/${courtId}/enable`)
