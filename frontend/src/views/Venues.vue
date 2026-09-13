<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listVenues, listCourts, getOccupancy } from '@/api/venue'
import { createReservation } from '@/api/reservation'

// ---------- 日期 ----------
function todayStr() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

// 可选日期范围：今天 ~ 今天+7 天（与后端 7 天窗口一致，前后端都拦一道更友好）
const date = ref(todayStr())
const maxDate = new Date(Date.now() + 7 * 86400000)
const maxDateStr = `${maxDate.getFullYear()}-${String(maxDate.getMonth() + 1).padStart(2, '0')}-${String(maxDate.getDate()).padStart(2, '0')}`

// ---------- 数据 ----------
const venues = ref([])
const currentVenueId = ref(null)
const courts = ref([])
const occupancyMap = ref({}) // { courtId: [ {start_time, end_time, kind} ] }
const loading = ref(false)

// ---------- 时段选择 ----------
// V1 场馆营业时段按 8:00 ~ 22:00 展示，每个格子代表 1 小时
const HOURS = Array.from({ length: 14 }, (_, i) => i + 8)

const dialogVisible = ref(false)
const activeCourt = ref(null)
const selStart = ref(null)
const selEnd = ref(null)
const submitting = ref(false)

const activeSlots = computed(() =>
  activeCourt.value ? occupancyMap.value[activeCourt.value.id] || [] : []
)

/** 某一小时是否已被占用（个人预约或活动占场都算） */
function isOccupied(hour) {
  return activeSlots.value.some((s) => {
    const sh = Number(s.start_time.slice(11, 13))
    const eh = Number(s.end_time.slice(11, 13)) || (Number(s.end_time.slice(11, 13)) === 0 ? 24 : 0)
    return hour >= sh && hour < eh
  })
}

/** 占用来源文案，用于悬浮提示 */
function occupiedBy(hour) {
  const s = activeSlots.value.find((x) => {
    const sh = Number(x.start_time.slice(11, 13))
    const eh = Number(x.end_time.slice(11, 13))
    return hour >= sh && hour < eh
  })
  if (!s) return ''
  return s.kind === 'ACTIVITY' ? '被活动占用' : '已被预约'
}

/** 某个格子是否落在当前选择的区间内 */
function inRange(hour) {
  if (selStart.value === null || selEnd.value === null) return false
  return hour >= selStart.value && hour < selEnd.value
}

function pickHour(hour) {
  if (isOccupied(hour)) {
    ElMessage.warning('该时段已被占用，请另选')
    return
  }
  // 第一次点：确定起点；第二次点：确定终点
  if (selStart.value === null || selEnd.value !== null) {
    selStart.value = hour
    selEnd.value = null
    return
  }
  if (hour <= selStart.value) {
    // 往前点，就把它当成新的起点
    selStart.value = hour
    selEnd.value = null
    return
  }
  // 终点必须整段空闲
  for (let h = selStart.value; h < hour; h++) {
    if (isOccupied(h)) {
      ElMessage.warning('所选区间内包含已被占用的时段')
      return
    }
  }
  selEnd.value = hour
}

const rangeText = computed(() => {
  if (selStart.value === null) return '尚未选择'
  if (selEnd.value === null) return `${pad(selStart.value)}:00 起（再点一个格子确定结束时间）`
  return `${pad(selStart.value)}:00 — ${pad(selEnd.value)}:00`
})

function pad(n) {
  return String(n).padStart(2, '0')
}

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

/** 球场当天是否完全空闲（给卡片加个角标） */
function freeCount(courtId) {
  const list = occupancyMap.value[courtId] || []
  return HOURS.filter((h) => !isOccupiedIn(list, h)).length
}

function isOccupiedIn(list, hour) {
  return list.some((s) => {
    const sh = Number(s.start_time.slice(11, 13))
    const eh = Number(s.end_time.slice(11, 13))
    return hour >= sh && hour < eh
  })
}

// ---------- 交互 ----------
function openCourt(court) {
  activeCourt.value = court
  selStart.value = null
  selEnd.value = null
  dialogVisible.value = true
}

async function submitReservation() {
  if (selStart.value === null || selEnd.value === null) {
    ElMessage.warning('请先选择完整的时段')
    return
  }
  submitting.value = true
  try {
    await createReservation({
      court_id: activeCourt.value.id,
      start_time: `${date.value}T${pad(selStart.value)}:00:00`,
      end_time: `${date.value}T${pad(selEnd.value)}:00:00`
    })
    ElMessage.success('预约成功')
    dialogVisible.value = false
    await loadOccupancy()
  } catch (e) {
    // 失败原因（窗口/冲突/每天累计上限等）由拦截器提示
  } finally {
    submitting.value = false
  }
}

watch(currentVenueId, loadCourts)
watch(date, loadOccupancy)

onMounted(async () => {
  await loadVenues()
  await loadCourts()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">场馆预约</h2>
        <p class="page-sub">
          {{ venues.find((v) => v.id === currentVenueId)?.name || '加载中' }}
          <span v-if="venues.find((v) => v.id === currentVenueId)?.address">
            · {{ venues.find((v) => v.id === currentVenueId).address }}
          </span>
        </p>
      </div>

      <el-date-picker
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="选择日期"
        :clearable="false"
        :disabled-date="(d) => d < new Date(new Date().toDateString()) || d > maxDate"
      />
    </div>

    <el-alert
      class="tip"
      type="info"
      :closable="false"
      show-icon
      title="可预约未来 7 天内的场地；每格代表 1 小时（8:00–22:00）。同一时段每块场只能被一个人预约。"
    />

    <div v-loading="loading" class="card-grid court-grid">
      <el-card
        v-for="court in courts"
        :key="court.id"
        shadow="hover"
        class="court-card"
        @click="openCourt(court)"
      >
        <div class="court-top">
          <span class="court-name">{{ court.name }}</span>
          <el-tag
            :type="freeCount(court.id) === 0 ? 'danger' : 'success'"
            size="small"
            effect="light"
          >
            {{ freeCount(court.id) === 0 ? '已满' : `空闲 ${freeCount(court.id)} 小时` }}
          </el-tag>
        </div>

        <div class="court-slots">
          <span
            v-for="h in HOURS"
            :key="h"
            class="mini-slot"
            :class="{ taken: isOccupiedIn(occupancyMap[court.id] || [], h) }"
          />
        </div>

        <div class="court-foot muted">
          {{ date }} · 点击选择时段
        </div>
      </el-card>

      <el-empty v-if="!loading && !courts.length" description="暂无可用球场" class="empty-box" />
    </div>

    <!-- 时段选择 -->
    <el-dialog v-model="dialogVisible" :title="activeCourt?.name + ' · ' + date" width="560px">
      <div class="slot-legend">
        <span class="legend-item"><i class="dot free" />空闲（可点选）</span>
        <span class="legend-item"><i class="dot taken" />已被占用</span>
        <span class="legend-item"><i class="dot picked" />已选</span>
      </div>

      <div class="slot-grid">
        <div
          v-for="h in HOURS"
          :key="h"
          class="slot"
          :class="{
            taken: isOccupied(h),
            picked: inRange(h)
          }"
          :title="isOccupied(h) ? occupiedBy(h) : `${pad(h)}:00 - ${pad(h + 1)}:00`"
          @click="pickHour(h)"
        >
          {{ pad(h) }}
        </div>
      </div>

      <div class="picked-line">
        已选时段：<strong>{{ rangeText }}</strong>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="selStart === null || selEnd === null"
          @click="submitReservation"
        >
          确认预约
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tip {
  margin-bottom: 16px;
}

.court-card {
  cursor: pointer;
  border-radius: 10px;
}

.court-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.court-name {
  font-size: 16px;
  font-weight: 600;
}

.court-slots {
  display: flex;
  gap: 3px;
  margin-bottom: 12px;
}

.mini-slot {
  flex: 1;
  height: 22px;
  border-radius: 3px;
  background: #e1f3d8;
}

.mini-slot.taken {
  background: #fde2e2;
}

.court-foot {
  font-size: 12px;
}

.slot-legend {
  display: flex;
  gap: 18px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #606266;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.dot {
  width: 11px;
  height: 11px;
  border-radius: 3px;
  display: inline-block;
}

.dot.free {
  background: #e1f3d8;
}

.dot.taken {
  background: #fde2e2;
}

.dot.picked {
  background: #409eff;
}

.slot-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
}

.slot {
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: #e1f3d8;
  color: #529b2e;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
  transition: all 0.12s;
}

.slot:hover {
  transform: translateY(-1px);
}

.slot.taken {
  background: #fde2e2;
  color: #c45656;
  cursor: not-allowed;
}

.slot.picked {
  background: #409eff;
  color: #fff;
}

.picked-line {
  margin-top: 16px;
  font-size: 14px;
}
</style>
