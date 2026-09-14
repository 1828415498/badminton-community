<script setup>
/**
 * Venues —— 场地与预约。
 *
 * <h2>改造前的问题</h2>
 * 这一页的时间表网格在前一轮已经做对了（球场=行 / 时段=列，用文字+底纹区分占用），
 * 但它整页仍是一张"数据表"：标题是资源名「场馆预约」，场馆自身的信息只剩
 * 标题下面一行小字（名称 + 地址）。用户看不出"这个场馆有几片场、今天还剩多少能打"，
 * 也就没有"我正在找一个可以打球的地方"的感觉。
 *
 * <h2>改造后的结构</h2>
 * 场馆本身升为页面主角：**h1 就是场馆名**（只有一片场馆时，场馆就是这个页面），
 * 下面直接给三个真实数字：几片场 / 这天可约多少小时 / 最早几点能订。
 * 日期排与时间表紧随其后。
 *
 * <h2>没有做的事</h2>
 * 设计稿里"场地图片""距离"这两项**不做**：venue 表没有经纬度（算不出距离），
 * 全项目也没有任何图片字段与图片资源。编一个"距你 1.2km"就是在骗人，
 * 所以视觉重量改由**排版与数字**承担 —— 这也正是这一版的设计取向。
 */
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { listVenues, listCourts, getOccupancy } from '@/api/venue'
import { createReservation } from '@/api/reservation'
import { AppButton, EmptyState, AppSkeleton } from '@/components'
import { pad, WEEK, parseTime, startOf } from '@/utils/datetime'

// ---------- 日期（未来 7 天窗口，和后端规则一致） ----------
const HOURS = Array.from({ length: 14 }, (_, i) => i + 8) // 8:00 - 22:00

function ymd(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 某个小时是否已经过去 —— 今天已过去的时段点不了（后端也会拦，前端先标出来）。
 * 用 parseTime 而不是 `new Date(字符串)`：后者在空格分隔格式上各引擎解读不同。
 */
function pastSlot(hour, dayStr) {
  const p = parseTime(`${dayStr} ${pad(hour)}:00`)
  return !!p && p.date.getTime() <= Date.now()
}

/** 这一天是否已经完全约不到（晚上 9 点之后今天就没得约了） */
function dayExhausted(dayStr) {
  return HOURS.every((h) => pastSlot(h, dayStr))
}

const base = startOf(0)

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

const ttScroll = ref(null)
/** 只在首次拿到数据时自动横向滚动一次，之后不再干扰用户自己的位置 */
let autoScrolled = false

/**
 * 把最早可订的时刻滚到视野里。
 *
 * 为什么需要：网格是 14 列（8:00–22:00）的横向滚动表。在手机上只看得见 3 列，
 * 而如果是上午打开，最左边这几列全是"已过时段"（虚线）—— 用户的第一印象是
 * "这里什么都没有"，得自己往右滑才知道能订什么时候。桌面端 14 列放得下，不受影响。
 */
function revealEarliest() {
  const el = ttScroll.value
  if (!el || !el.scrollWidth || el.scrollWidth <= el.clientWidth) return
  const idx = earliestFree.value === null ? -1 : HOURS.indexOf(earliestFree.value)
  if (idx <= 0) return
  const grid = el.querySelector('.tt-grid')
  const cells = grid?.querySelectorAll('.tt-h')
  const target = cells?.[idx]
  if (!target) return
  // 目标列在表格内容里的左偏移，减去吸左的球场名列宽，避免被压在它下面
  const offset = target.getBoundingClientRect().left - grid.getBoundingClientRect().left
  const labelW = grid.querySelector('.tt-row')?.offsetWidth ?? 0
  el.scrollLeft = Math.max(0, offset - labelW)
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
    if (!autoScrolled) {
      autoScrolled = true
      await nextTick()
      revealEarliest()
    }
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

// ---------- 场馆身份区的三个数字 ----------
// 全部由现有数据算出：球场数来自接口，可约小时与最早起订时间来自占用表。
// 不编"距离"（没有经纬度）、不做"场馆图片"（没有图片字段）。

/** 所选日期一共还能约多少小时（所有球场求和） */
const totalFree = computed(() =>
  courts.value.reduce((n, c) => n + freeCount(c.id), 0)
)

/** 所选日期还剩多少个可约格子（用于区分"全被订满"和"这天已经过完了"） */
const totalBookable = computed(() =>
  courts.value.reduce(
    (n, c) => n + HOURS.filter((h) => slotState(c.id, h) !== 'past').length,
    0
  )
)

/** 最早的起订时刻：哪一块场都行，能打的最早那小时 */
const earliestFree = computed(() => {
  for (const h of HOURS) {
    if (courts.value.some((c) => slotState(c.id, h) === 'free')) return h
  }
  return null
})

const selDayLabel = computed(
  () => days.find((d) => d.value === date.value)?.label ?? ''
)

/** 可约小时数的文案：约满 / 已过 / 具体小时数 */
const freeText = computed(() => {
  if (!courts.value.length) return '—'
  if (totalFree.value > 0) return `${totalFree.value} 小时`
  return totalBookable.value === 0 ? '已过' : '已约满'
})

/**
 * 最早可订。
 * 只有今天（或已过去的时段之后）才值得说"最早几点" —— 未来日期整段开放，
 * 显示"08:00"没有任何信息量。所以未来日期这一格换成"全天开放"。
 */
const earliestText = computed(() => {
  if (!courts.value.length) return '—'
  if (earliestFree.value === null) return '—'
  const isToday = date.value === days[0].value
  if (!isToday && earliestFree.value === HOURS[0]) return '全天开放'
  return `${pad(earliestFree.value)}:00`
})

/**
 * 面板的三种视图态。
 *
 * 用显式状态而不是 `v-loading` 指令：Element 的加载遮罩会盖住整块面板转圈，
 * 而这里两种"没内容"是完全不同的事 —— 首次加载（给骨架）与场馆没有开放场地
 * （给空状态 + 下一步）。混在一起会两种情况都表现不对。
 */
const viewState = computed(() => {
  if (loading.value && !courts.value.length) return 'loading'
  if (!courts.value.length) return 'empty'
  return 'ready'
})

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
    <!-- 场馆身份区。
         h1 是场馆名而不是"场馆预约"：只有一片场馆时，场馆本身就是这个页面。
         再套一个泛化标题 + 一张信息卡，就是在同一件事上叠两层。 -->
    <header class="venue">
      <div class="venue__row">
        <h1 class="venue__name">{{ currentVenue?.name || '场地' }}</h1>
        <el-select
          v-if="venues.length > 1"
          v-model="currentVenueId"
          class="venue__pick"
          placeholder="选择场馆"
        >
          <el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" />
        </el-select>
      </div>

      <p v-if="currentVenue?.address" class="venue__addr">{{ currentVenue.address }}</p>

      <!-- 三个真数字。距离与场馆图片不做 —— 表里没有经纬度、也没有图片字段，
           编一个"距你 1.2km"就是在骗人。视觉重量因此由排版与数字承担。 -->
      <dl class="venue__stats">
        <div class="vstat">
          <dt>场地</dt>
          <dd class="tnum">{{ courts.length ? `${courts.length} 片` : '—' }}</dd>
        </div>
        <div class="vstat">
          <dt>{{ selDayLabel || '可约' }}可约</dt>
          <dd class="tnum">{{ freeText }}</dd>
        </div>
        <div class="vstat">
          <dt>最早可订</dt>
          <dd class="tnum">{{ earliestText }}</dd>
        </div>
      </dl>
    </header>

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
      <!-- 首屏加载：骨架的形状与真实内容一致（行高、间距），避免"先空后跳" -->
      <div v-if="viewState === 'loading'" class="tt-skeleton">
        <AppSkeleton v-for="i in 5" :key="i" height="42px" />
      </div>

      <EmptyState
        v-else-if="viewState === 'empty'"
        title="这个场馆还没有开放场地"
        description="等管理员开放场地后就能在这里预约了。"
      />

      <template v-else>
        <div ref="ttScroll" class="tt-scroll">
          <!-- 切换日期时 courts 已经在手上，用降透明度代替遮罩转圈：
               用户还能看到上一秒的排期，不会整块闪白 -->
          <div class="tt-grid" :class="{ 'is-busy': loading }">
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

        <div class="tt-legend">
          <span class="lg"><i class="lg-sw"></i>空闲</span>
          <span class="lg"><i class="lg-sw booked"></i>已被预约</span>
          <span class="lg"><i class="lg-sw activity"></i>活动占场</span>
          <span class="lg"><i class="lg-sw sel"></i>你选的时段</span>
          <span class="lg"><i class="lg-sw past"></i>已过时段</span>
        </div>

      </template>
    </section>

    <!-- 待确认的选择。
         刻意放在 .panel **之外**：.panel 有 overflow:hidden（裁圆角用），
         而 overflow 非 visible 的祖先会让子元素的 position:sticky 失效 ——
         这是最经典的 sticky 失效原因。移出来才能钉在视口底部。 -->
    <div v-if="sel.courtId" class="pick-bar">
      <div class="pick-info">
        <span class="pick-court">{{ sel.courtName }}</span>
        <span class="pick-range tnum">{{ rangeText }}</span>
      </div>
      <div class="pick-actions">
        <AppButton variant="ghost" @click="clearSel">清除</AppButton>
        <AppButton :loading="submitting" :disabled="sel.end === null" @click="submit">
          确认预约
        </AppButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 场馆身份区 ----------
   刻意不做成卡片：这一页已经有一个大面板（时间表），再套一张信息卡
   就会变成"Card + Card"。靠留白与一条发丝线划开就够了。 */
.venue {
  margin-bottom: var(--s6);
}

.venue__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s4);
  flex-wrap: wrap;
}

.venue__name {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--fs-h1);
  font-weight: 700;
  letter-spacing: -0.022em;
  line-height: 1.1;
  color: var(--ink);
}

.venue__pick {
  width: 180px;
}

.venue__addr {
  margin: var(--s2) 0 0;
  font-size: var(--fs-body);
  color: var(--ink-2);
}

/* 三个真数字。用视觉重量而不是卡片来强调 —— 数字本身就是这一页最有力的排版元素。 */
.venue__stats {
  display: flex;
  gap: var(--s7);
  flex-wrap: wrap;
  margin: var(--s5) 0 0;
  padding-top: var(--s4);
  border-top: 1px solid var(--line);
}

.vstat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.vstat dt {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.vstat dd {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--fs-h3);
  font-weight: 700;
  letter-spacing: -0.015em;
  line-height: 1.15;
  color: var(--ink);
}

/* 首屏骨架：与时间表同样的纵向节奏 */
.tt-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--s3);
  padding: var(--s5);
}

/* 切换日期时降透明度，而不是盖一层转圈遮罩：
   用户还能看到上一秒的排期，不会整块闪白 */
.tt-grid.is-busy {
  opacity: 0.45;
  pointer-events: none;
  transition: opacity 0.15s;
}

/* 首屏骨架：与时间表同样的纵向节奏 */
.tt-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--s3);
  padding: var(--s5);
}

/* 切换日期时降透明度，而不是盖一层转圈遮罩：
   用户还能看到上一秒的排期，不会整块闪白 */
.tt-grid.is-busy {
  opacity: 0.45;
  pointer-events: none;
  transition: opacity 0.15s;
}

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
/* 确认条：选中时段后钉在视口底部，滚到哪都点得到。
   实测过 1280x720 / 1366x768 与手机 —— 放在表格末尾时它在视口之外，
   用户点完第一个格子还得往下找按钮。 */
.pick-bar {
  position: sticky;
  bottom: var(--s4);
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s4);
  flex-wrap: wrap;
  margin-top: var(--s4);
  padding: var(--s3) var(--s4);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  /* 浮在内容之上，和弹层同类，所以这里用阴影是合理的（全站默认仍以描边为主） */
  box-shadow: 0 8px 24px rgba(16, 19, 17, 0.1);
  transition: opacity 0.16s;
}

/* 窄屏要让开底部 Tab 的高度，否则操作条会被导航挡住 */
@media (max-width: 640px) {
  .pick-bar {
    bottom: calc(var(--tabbar-h) + var(--s3));
  }
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
