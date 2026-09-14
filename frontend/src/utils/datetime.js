/**
 * 时间工具 —— 全站唯一的时间解析/格式化入口。
 *
 * <h2>为什么必须抽出来</h2>
 * 后端返回的时间是 `2026-09-14T17:00:00` 这种**原始 ISO 串**。
 * 直接渲染给用户会看到 "2026-09-14T17:00:00 ~ 19:00" —— 这是
 * "数据库字段可视化"最典型的证据。所以每个展示位置都要先解析。
 *
 * <h2>两个踩过的坑（改这个文件前先读）</h2>
 * <ol>
 *   <li><b>天数差必须只比年月日。</b> 用"带时分的时间"减"当天零点"，
 *       9/14 19:00 会得到 1.79 天，`Math.round` 进位成 2 —— 「明天」被显示成「后天」。</li>
 *   <li><b>不要用 `new Date(str)` 解析。</b> 后端串带 `T`，部分串不带，
 *       且 Safari 对 `2026-09-14 17:00:00` 这种空格分隔是 Invalid Date。
 *       所以这里手工切分，两种分隔符都吃得下。</li>
 * </ol>
 */

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

export function pad(n) {
  return String(n).padStart(2, '0')
}

/** 今天 00:00 起，往后 daysAhead 天的零点 */
export function startOf(daysAhead = 0) {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() + daysAhead)
  return d
}

/** 今天起往后 daysAhead 天的 23:59:59 */
export function endOf(daysAhead = 0) {
  const d = startOf(daysAhead)
  d.setHours(23, 59, 59, 0)
  return d
}

/** Date → 后端接受的 "YYYY-MM-DDTHH:mm:ss"（本地时区，不转 UTC） */
export function iso(d) {
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  )
}

/**
 * 解析后端时间串 → { date, h, m }；无法解析时返回 null。
 * 兼容 `2026-09-14T17:00:00` 与 `2026-09-14 17:00:00`。
 */
export function parseTime(s) {
  if (!s) return null
  const [d, t = '00:00:00'] = String(s).replace('T', ' ').split(' ')
  const [Y, M, D] = d.split('-').map(Number)
  const [h, m] = t.split(':').map(Number)
  if (!Y || !M || !D) return null
  return { date: new Date(Y, M - 1, D, h, m), h, m }
}

/**
 * 距今天几天（0=今天，1=明天）。**只比年月日**，见文件头坑 1。
 */
export function dayDiff(date) {
  const a = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  return Math.round((a - startOf(0).getTime()) / 86400000)
}

/** 统一成 "YYYY-MM-DD HH:mm:ss" 定长串，同长度下字典序即时间序 */
export function normTime(s) {
  return String(s || '').replace('T', ' ').slice(0, 19)
}

/**
 * 按开始时间升序。
 *
 * <p>后端公开列表是 `ORDER BY a.start_time DESC`（最新的在前），
 * 对"接下来有什么球局"这种发现型列表是反的。**后端不改**，
 * 所以在展示层重排。</p>
 */
export function sortByStartAsc(list) {
  return [...(list || [])].sort((a, b) =>
    normTime(a.start_time).localeCompare(normTime(b.start_time))
  )
}

/** 相对日名：今天 / 明天 / 后天 / 周三 */
export function dayName(date) {
  const diff = dayDiff(date)
  if (diff === 0) return '今天'
  if (diff === 1) return '明天'
  if (diff === 2) return '后天'
  return WEEK[date.getDay()]
}

/** "今天 17:00" / "周三 10:00" —— 列表里最常用的一行 */
export function whenShort(s) {
  const p = parseTime(s)
  if (!p) return ''
  return `${dayName(p.date)} ${pad(p.h)}:${pad(p.m)}`
}

/** "17:00–19:00"；跨天时标出「次日」 */
export function timeRange(startStr, endStr) {
  const a = parseTime(startStr)
  const b = parseTime(endStr)
  if (!a) return ''
  if (!b) return `${pad(a.h)}:${pad(a.m)}`
  const hm = `${pad(b.h)}:${pad(b.m)}`
  // 跨天要说清楚是第二天 —— 只写 "22:00–01:00" 会被读成倒着的时间段
  const head = dayDiff(b.date) !== dayDiff(a.date) ? `次日 ${hm}` : hm
  return `${pad(a.h)}:${pad(a.m)}–${head}`
}

/** "9月14日 周三" —— 详情页用 */
export function dateLong(s) {
  const p = parseTime(s)
  if (!p) return ''
  return `${p.date.getMonth() + 1}月${p.date.getDate()}日 ${WEEK[p.date.getDay()]}`
}

/**
 * 相对时间：社区动态流用。
 *
 * <p>规则：**24 小时内一律说"N 小时前 / N 分钟前"** —— 比"昨天 21:00"更精确，
 * 也不会出现"今天 01:00"与"昨天 23:00"看起来只差两小时的错觉。
 * 超过 24 小时才切到按天表述（昨天 / 前天 / 周三 / 9月3日）。</p>
 *
 * <p>按天判断时**只比年月日**（`dayDiff`），理由见文件头。</p>
 */
export function fromNow(s) {
  const p = parseTime(s)
  if (!p) return ''
  const delta = Date.now() - p.date.getTime()
  if (delta < 0) return '刚刚' // 客户端时钟偏差，不要显示"负几小时前"
  if (delta < 60000) return '刚刚'
  if (delta < 3600000) return `${Math.floor(delta / 60000)} 分钟前`
  if (delta < 86400000) return `${Math.floor(delta / 3600000)} 小时前`

  const d = dayDiff(p.date)
  const hm = `${pad(p.h)}:${pad(p.m)}`
  if (d === 1) return `昨天 ${hm}`
  if (d === 2) return `前天 ${hm}`
  if (d > 2 && d <= 6) return `${WEEK[p.date.getDay()]} ${hm}`
  return `${p.date.getMonth() + 1}月${p.date.getDate()}日`
}

export { WEEK }
