<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listVenues, listCourts, getOccupancy } from '@/api/venue'
import { createReservation } from '@/api/reservation'

// ---------- 日期（未来 7 天窗口，和后端规则一致） ----------
const HOURS = Array.from({ length: 14 }, (_, i) => i + 8) // 8:00 - 22:00
const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function pad(n) {
  return String(n).padStart(2, '0')
}

function ymd(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 某个小时是否已经过去 —— 今天已过去的时段点不了（后端也会拦，前端先标出来） */
function pastSlot(hour, dayStr) {
  return new Date(`${dayStr}T${pad(hour)}:00:00`).getTime() <= Date.now()
}

/** 这一天是否已经完全约不到（晚上 9 点之后今天就没得约了） */
function dayExhausted(dayStr) {
  return HOURS.every((h) => pastSlot(h, dayStr))
}

const base = new Date()
base.setHours(0, 0, 0, 0)

// 8 个日期块：今天 + 未来 7 天。做成横向一排，比弹出日历少两次点击。
const days = Array.from({ length: 8 }, (_, i) => {
  const d = new Date(base.getTime() + i * 86400000)
  return {
    value: ymd(d),
    label: i === 0 ? '今天' : i === 1 ? '明天' : WEEK[d.getDay()],
    sub: `${d.getMonth() + 1}/${d.getDate()}`
  }
})

// 今天已经约不到任何时段时直接落到明天，省得用户进来看到一整片灰
const date = ref(dayExhausted(days[0].value) ? days[1].value : days[0].value)

function isPast(hour) {
  return pastSlot(hour, date.value)
}

// ---------- 数据 ----------
const venues = ref([])
const currentVenueId = ref(null)
const courts = ref([])
const occupancyMap = ref({}) // { courtId: [ { start_time, end_time, kind } ] }
const loading = ref(false)

function hourOf(iso) {
  return Number(String(iso).slice(11, 13))
}

/** 占用行覆盖的小时区间 [起, 止) */
function spanOf(s) {
  const sh = hourOf(s.start_time)
  let eh = hourOf(s.end_time)
  if (eh === 0) eh = 24
  return [sh, eh]
}

/**
 * 某块场某小时的占用情况。
 * 返回 null = 空闲；否则返回那条占用记录。
 */
function occupancyAt(courtId, hour) {
  const list = occupancyMap.value[courtId] || []
  for (const s of list) {
    const [sh, eh] = spanOf(s)
    if (hour >= sh && hour < eh) return s
  }
  return null
}

/** 格子的状态：free / booked（个人预约）/ activity（活动占场） */
function slotState(courtId, hour) {
  if (isPast(hour)) return 'past'
  const s = occupancyAt(courtId, hour)
  if (!s) return 'free'
  return String(s.kind).toUpperCase() === 'ACTIVITY' ? 'activity' : 'booked'
}

function slotText(state) {
  return state === 'activity' ? '活动占场' : '已被预约'
}

/** 悬浮提示：把整段占用区间写出来，比只说"已占用"有用 */
function occTip(courtId, hour) {
  const s = occupancyAt(courtId, hour)
  if (!s) return ''
  const [sh, eh] = spanOf(s)
  return `${slotText(slotState(courtId, hour))} ${pad(sh)}:00–${pad(eh)}:00`
}

/** 这块场当天还剩几小时空闲 */
function freeCount(courtId) {
  let n = 0
  for (const h of HOURS) if (slotState(courtId, h) === 'free') n++
  return n
}

/** 一行右侧的说明：要区分"全被订满了"和"这一天已经过完了" */
function rowNote(courtId) {
  const bookable = HOURS.filter((h) => slotState(courtId, h) !== 'past').length
  if (bookable === 0) return '今日已过'
  const free = freeCount(courtId)
  return free === 0 ? '当天已满' : `空闲 ${free} 小时`
}

// ---------- 时段选择 ----------
// 点第一格 = 起点，点第二格 = 终点；换一块场则重新开始。
const sel = ref({ courtId: null, courtName: '', start: null, end: null })

function clearSel() {
  sel.value = { courtId: null, courtName: '', start: null, end: null }
}

function isStart(courtId, hour) {
  return sel.value.courtId === courtId && sel.value.start === hour && sel.value.end === null
}

function inSel(courtId, hour) {
  const { courtId: cid, start, end } = sel.value
  return cid === courtId && start !== null && end !== null && hour >= start && hour < end
}

function pick(court, hour) {
  if (slotState(court.id, hour) !== 'free') return

  const cur = sel.value
  // 换了一块场：重新开始选
  if (cur.courtId !== court.id) {
    sel.value = { courtId: court.id, courtName: court.name, start: hour, end: null }
    return
  }
  // 已经选完整段了：从这一格重新开始
  if (cur.end !== null) {
    sel.value = { ...cur, start: hour, end: null }
    return
  }
  // 点同一个格子 = 只订 1 小时
  if (hour === cur.start) {
    sel.value = { ...cur, end: hour + 1 }
    return
  }
  // 往前点：把它当成新的起点
  if (hour < cur.start) {
    sel.value = { ...cur, start: hour, end: null }
    return
  }
  // 终点：中间不能夹着被占用的格子
  for (let h = cur.start; h < hour; h++) {
    if (slotState(court.id, h) !== 'free') {
      ElMessage.warning('所选区间里有已被占用的时段，请重选')
      return
    }
  }
  sel.value = { ...cur, end: hour }
}

const rangeText = computed(() => {
  const { start, end } = sel.value
  if (start === null) return ''
  if (end === null) return `${pad(start)}:00 起 · 再点一个格子确定结束时间`
  return `${pad(start)}:00 — ${pad(end)}:00 · 共 ${end - start} 小时`
})

// ---------- 加载 ----------
async function loadVenues() {
  venues.value = await listVenues()
  if (venues.value.length && !currentVenueId.value) {
    currentVenueId.value = venues.value[0].id
  }
}

async function loadCourts() {
  if (!currentVenueId.value) return
  courts.value = await listCourts(currentVenueId.value, { status: 1 })
  await loadOccupancy()
}

async function loadOccupancy() {
  loading.value = true
  try {
    const entries = await Promise.all(
      courts.value.map(async (c) => {
        const res = await getOccupancy(c.id, date.value)
        return [c.id, res.occupied || []]
      })
    )
    occupancyMap.value = Object.fromEntries(entries)
  } finally {
    loading.value = false
  }
}

// ---------- 提交 ----------
const submitting = ref(false)

async function submit() {
  if (sel.value.end === null) {
    ElMessage.warning('请先选完整的时段')
    return
  }
  submitting.value = true
  try {
    await createReservation({
      court_id: sel.value.courtId,
      start_time: `${date.value}T${pad(sel.value.start)}:00:00`,
      end_time: `${date.value}T${pad(sel.value.end)}:00:00`
    })
    ElMessage.success(
      `${sel.value.courtName} ${pad(sel.value.start)}:00–${pad(sel.value.end)}:00 预约成功`
    )
    clearSel()
    await loadOccupancy()
  } catch (e) {
    // 具体失败原因（时间窗口 / 场地冲突 / 重叠上限）由响应拦截器统一提示
  } finally {
    submitting.value = false
  }
}

const currentVenue = computed(() => venues.value.find((v) => v.id === currentVenueId.value))

watch(currentVenueId, () => {
  clearSel()
  loadCourts()
})

watch(date, () => {
  clearSel()
  loadOccupancy()
})

onMounted(async () => {
  await loadVenues()
  await loadCourts()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">场馆预约</h1>
        <p class="page-sub">
          {{ currentVenue?.name || '加载中' }}
          <template v-if="currentVenue?.address"> · {{ currentVenue.address }}</template>
        </p>
      </div>
      <el-select
        v-if="venues.length > 1"
        v-model="currentVenueId"
        style="width: 180px"
        placeholder="选择场馆"
      >
        <el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" />
      </el-select>
    </div>

    <!-- 日期排：今天 + 未来 7 天 -->
    <div class="daybar" role="group" aria-label="选择预约日期">
      <button
        v-for="d in days"
        :key="d.value"
        type="button"
        class="day"
        :class="{ on: d.value === date, over: d.value === days[0].value && dayExhausted(d.value) }"
        :aria-pressed="d.value === date"
        :title="d.value === days[0].value && dayExhausted(d.value) ? '今天的营业时段已经过去' : ''"
        @click="date = d.value"
      >
        <span class="day-label">{{ d.label }}</span>
        <span class="day-sub tnum">{{ d.sub }}</span>
      </button>
    </div>

    <div class="hint-row">
      <svg
        width="14"
        height="14"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="9" />
        <path d="M12 11v5M12 8h.01" stroke-linecap="round" />
      </svg>
      每格 1 小时 · 点两个空格确定起止时段 · 同一时段每块场只能被一个人占用
    </div>

    <section class="panel">
      <div class="tt-scroll">
        <div v-loading="loading" class="tt-grid">
          <!-- 表头：小时刻度 -->
          <div class="tt-corner"></div>
          <div v-for="h in HOURS" :key="'h' + h" class="tt-h tnum">{{ pad(h) }}</div>

          <!-- 每一块场 = 一行 -->
          <template v-for="court in courts" :key="court.id">
            <div class="tt-row">
              <span class="tt-court">{{ court.name }}</span>
              <span class="tt-free tnum">{{ rowNote(court.id) }}</span>
            </div>

            <template v-for="h in HOURS" :key="court.id + '-' + h">
              <!-- 已经过去的时段：不可选，虚线框表示"这里没有东西" -->
              <div
                v-if="slotState(court.id, h) === 'past'"
                class="tt-cell tt-off is-past"
                title="这个时段已经过去了"
                :aria-label="`${court.name} ${pad(h)}:00，时段已过去`"
              ></div>
              <button
                v-else-if="slotState(court.id, h) === 'free'"
                type="button"
                class="tt-cell"
                :class="{ 'is-start': isStart(court.id, h), 'is-sel': inSel(court.id, h) }"
                :aria-pressed="inSel(court.id, h)"
                :aria-label="`${court.name} ${pad(h)}:00 到 ${pad(h + 1)}:00，空闲，点击选择`"
                @click="pick(court, h)"
              ></button>
              <!-- 被占用的格子不可操作，所以用 div 而不是 button：不进 Tab 顺序 -->
              <div
                v-else
                class="tt-cell tt-off"
                :class="'is-' + slotState(court.id, h)"
                :title="occTip(court.id, h)"
                :aria-label="`${court.name} ${pad(h)}:00，${slotText(slotState(court.id, h))}`"
              >
                {{ slotState(court.id, h) === 'activity' ? '活' : '约' }}
              </div>
            </template>
          </template>
        </div>
      </div>

      <div v-if="!loading && !courts.length" class="empty">
        <div class="empty-mark">
          <svg
            width="22"
            height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            aria-hidden="true"
          >
            <rect x="3" y="5" width="18" height="14" rx="2" />
            <path d="M3 12h18M12 5v14" />
          </svg>
        </div>
        <p class="empty-title">这个场馆还没有开放球场</p>
        <p class="empty-sub">等管理员开放场地后就能预约了</p>
      </div>

      <div v-else class="tt-legend">
        <span class="lg"><i class="lg-sw"></i>空闲</span>
        <span class="lg"><i class="lg-sw booked"></i>已被预约</span>
        <span class="lg"><i class="lg-sw activity"></i>活动占场</span>
        <span class="lg"><i class="lg-sw sel"></i>你选的时段</span>
        <span class="lg"><i class="lg-sw past"></i>已过时段</span>
      </div>

      <!-- 待确认的选择 -->
      <div v-if="sel.courtId" class="pick-bar">
        <div class="pick-info">
          <span class="pick-court">{{ sel.courtName }}</span>
          <span class="pick-range tnum">{{ rangeText }}</span>
        </div>
        <div class="pick-actions">
          <el-button text @click="clearSel">清除</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="sel.end === null"
            @click="submit"
          >
            确认预约
          </el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ---------- 日期排 ---------- */
.daybar {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.day {
  flex: 0 0 auto;
  min-width: 76px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  font-family: inherit;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  transition: border-color 0.14s, background 0.14s;
}

.day:hover {
  border-color: var(--brand-600);
}

.day-label {
  font-size: 13px;
  color: var(--ink-2);
}

.day-sub {
  font-size: 11px;
  color: var(--ink-3);
}

/*
  选中的日期 = 品牌色填充。
  ⚠️ 荧光底上必须配深色文字：白字对比度只有 1.3:1，实测完全不可读。
*/
.day.on {
  background: var(--brand);
  border-color: var(--brand);
}

/* 今天已经过完了（比如晚上 9 点后）：压暗提示，不要让人白点一下 */
.day.over:not(.on) {
  opacity: 0.5;
}

.day.on .day-label {
  color: var(--brand-ink);
  font-weight: 600;
}

.day.on .day-sub {
  color: rgba(16, 19, 17, 0.72);
}

/* ---------- 预约网格 ---------- */
.tt-scroll {
  overflow-x: auto;
}

.tt-grid {
  display: grid;
  grid-template-columns: 128px repeat(14, minmax(46px, 1fr));
  /* 2px 间隙 + 选中格 1px 外环 = 相邻的选中格刚好连成一段连续时段，
     同时和未选中的格子之间仍留 1px 白缝 */
  gap: 2px;
  padding: 16px;
  min-width: 740px;
}

.tt-corner {
  height: 22px;
}

.tt-h {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 22px;
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-3);
}

/* 球场名一列：横向滚动时钉在左边 */
.tt-row {
  position: sticky;
  left: 0;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 1px;
  padding-right: 12px;
  background: var(--surface);
  border-right: 1px solid var(--line);
}

.tt-court {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
}

.tt-free {
  font-size: 11px;
  color: var(--ink-3);
  white-space: nowrap;
}

/* 格子 */
.tt-cell {
  height: 42px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: #fff;
  font-family: inherit;
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-3);
  cursor: pointer;
  transition: background 0.13s, border-color 0.13s, color 0.13s;
}

.tt-cell:hover:not(.tt-off):not(.is-sel):not(.is-start) {
  border-color: var(--brand-600);
  background: var(--brand-100);
}

.tt-cell:hover:not(.tt-off):not(.is-sel):not(.is-start)::after {
  content: '+';
  font-size: 15px;
  font-weight: 500;
  color: var(--brand-600);
}

/* 选中的时段 = 品牌色填充 + 深色文字（同上：荧光底不能配白字） */
.tt-cell.is-start,
.tt-cell.is-sel {
  background: var(--brand);
  border-color: var(--brand);
  color: var(--brand-ink);
  box-shadow: 0 0 0 1px var(--brand);
}

/*
  被占用的格子：靠"文字 + 底纹"区分，不靠颜色。
  个人预约是实底 +「约」，活动占场是斜纹 +「活」—— 灰度打印也能分辨。
*/
.tt-off {
  cursor: default;
}

.tt-off.is-booked {
  background: var(--line-2);
  border-color: var(--line-2);
}

.tt-off.is-past {
  background: transparent;
  border-style: dashed;
  border-color: var(--line);
}

/*
  活动占场用中性灰斜纹，不用品牌色 ——
  品牌色在本页专表"你选的时段"，不能被第二种含义占用。
*/
.tt-off.is-activity {
  background-color: var(--surface);
  background-image: repeating-linear-gradient(
    45deg,
    var(--line-2) 0 4px,
    transparent 4px 8px
  );
  border-color: var(--line-2);
}

/* ---------- 图例 ---------- */
.tt-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  padding: 0 16px 16px;
  font-size: 12px;
  color: var(--ink-3);
}

.lg {
  display: flex;
  align-items: center;
  gap: 6px;
}

.lg-sw {
  width: 14px;
  height: 14px;
  border-radius: 4px;
  border: 1px solid var(--line);
  background: #fff;
}

.lg-sw.booked {
  background: var(--line-2);
  border-color: var(--line-2);
}

.lg-sw.activity {
  background-color: var(--surface);
  background-image: repeating-linear-gradient(
    45deg,
    var(--line-2) 0 4px,
    transparent 4px 8px
  );
  border-color: var(--line-2);
}

.lg-sw.sel {
  background: var(--brand);
  border-color: var(--brand);
}

.lg-sw.past {
  background: transparent;
  border-style: dashed;
}

/* ---------- 待确认条 ---------- */
.pick-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  margin: 0 16px 16px;
  padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--brand-100);
}

.pick-info {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.pick-court {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
}

.pick-range {
  font-size: 13px;
  color: var(--ink-2);
}

.pick-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
