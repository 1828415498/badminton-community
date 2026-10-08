<script setup>
/**
 * Activities —— 球局列表。
 *
 * <h2>改造前的问题</h2>
 * 旧版是"卡片网格 + 分页"：9 张同一形态的白卡铺开、时间渲染成原始 ISO 串
 * （`2026-09-14T17:00:00 ~ 19:00`）、没有分组、也没有任何地方回答
 * "这场球我能不能去"（剩余名额是卡片最底下一条 6px 细进度条）。
 * 数据只有几条时，`auto-fill minmax(280px,1fr)` 还会把唯一一张卡拉成整屏白板。
 *
 * <h2>改造后</h2>
 * 按**时间**分组（今天 / 明天 / 接下来一周 / 更远 / 已结束），
 * 行式列表 —— 左列时间、右列剩余名额。这是发现型列表该有的形状：
 * 用户扫的是"什么时候"和"抢不抢得到"，不是"哪张卡更好看"。
 *
 * <h2>三个刻意的决定</h2>
 * <ol>
 *   <li><b>不做卡片网格。</b> 球局是时间序列，不是商品陈列。分组 + 行式
 *       比网格信息密度高、扫读快，也顺带解决了"一条数据拉成整屏"的崩坏。</li>
 *   <li><b>行内不放「加入」按钮。</b> 一屏六行六个按钮就是按钮泛滥；
 *       而且"都有谁去"只在详情页看得到，那才是下决心的地方。</li>
 *   <li><b>用「加载更多」而不是分页器。</b> 分页会把同一个时间分组切到两页上，
 *       分组的意义就没了。球局列表是连续浏览场景。</li>
 * </ol>
 *
 * <h2>排序约定</h2>
 * 公开列表是 `ORDER BY start_time DESC`（最新发布在前）。对"接下来有什么球局"
 * 这是反的，所以在展示层重排（`sortForView`）。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listActivities } from '@/api/activity'
import { AppButton, AppSkeleton, EmptyState } from '@/components'
import ActivityRow from '@/components/ActivityRow.vue'
import { iso, parseTime, dayDiff, normTime } from '@/utils/datetime'

const PAGE_SIZE = 20

const filters = reactive({ keyword: '', status: 'upcoming', startDate: '', endDate: '' })
// 已提交条件独立保存，编辑表单时不会改变正在浏览的分页结果。
const applied = ref({ ...filters, upcomingFrom: iso(new Date()) })
const loading = ref(true)
const loadingMore = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
let requestId = 0

const hasMore = computed(() => list.value.length < total.value)

function buildParams(nextPage) {
  const p = { page: nextPage, page_size: PAGE_SIZE }
  const { keyword, status, startDate, endDate } = applied.value
  if (keyword) p.keyword = keyword
  if (status === 'upcoming') {
    p.status = 1
    p.start_from = applied.value.upcomingFrom
  } else if (status === 'published') {
    p.status = 1
  } else if (status === 'ended') {
    p.status = 4
  }
  if (startDate) {
    const from = `${startDate}T00:00:00`
    if (!p.start_from || from > p.start_from) p.start_from = from
  }
  if (endDate) p.start_to = `${endDate}T23:59:59`
  return p
}

async function fetchPage(nextPage, append = false) {
  const currentRequest = ++requestId
  if (append) loadingMore.value = true
  else loading.value = true
  try {
    const res = await listActivities(buildParams(nextPage))
    if (currentRequest !== requestId) return
    const rows = res.list || []
    list.value = append ? [...list.value, ...rows] : rows
    total.value = res.total ?? list.value.length
    page.value = nextPage
  } catch (e) {
    if (currentRequest !== requestId) return
    if (!append) {
      list.value = []
      total.value = 0
    }
  } finally {
    if (currentRequest === requestId) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

function search() {
  if (filters.startDate && filters.endDate && filters.startDate > filters.endDate) {
    ElMessage.warning('开始日期不能晚于结束日期')
    return
  }
  const now = iso(new Date())
  if (filters.status === 'upcoming' && filters.endDate && filters.endDate < now.slice(0, 10)) {
    ElMessage.warning('即将开始的球局不能查询过去的日期，请调整日期或活动状态')
    return
  }
  applied.value = { ...filters, keyword: filters.keyword.trim(), upcomingFrom: now }
  fetchPage(1)
}

function resetSearch() {
  Object.assign(filters, { keyword: '', status: 'all', startDate: '', endDate: '' })
  search()
}

function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  fetchPage(page.value + 1, true)
}

// ---------- 分组 ----------

/** 球局是否已经打完：比结束时间，不是比开始时间（进行中的球局不算结束） */
function isOver(a) {
  const end = parseTime(a.end_time) || parseTime(a.start_time)
  return !!end && end.date.getTime() < Date.now()
}

const BUCKETS = [
  { key: 'today', label: '今天', match: (d) => d === 0 },
  { key: 'tomorrow', label: '明天', match: (d) => d === 1 },
  { key: 'week', label: '接下来一周', match: (d) => d >= 2 && d <= 7 },
  { key: 'later', label: '更远', match: (d) => d > 7 },
  { key: 'past', label: '已结束', match: () => false } // 兜底：由 isOver 单独收集
]

/**
 * 排序：未结束的按时间升序在前（最近的先看到），已结束的按时间降序垫底。
 * 后端给的是 DESC，这里重排；时间串定长，字典序即时间序。
 */
function sortForView(rows) {
  const open = []
  const past = []
  for (const a of rows) {
    if (!parseTime(a.start_time)) continue
    ;(isOver(a) ? past : open).push(a)
  }
  open.sort((a, b) => normTime(a.start_time).localeCompare(normTime(b.start_time)))
  past.sort((a, b) => normTime(b.start_time).localeCompare(normTime(a.start_time)))
  return { open, past }
}

const groups = computed(() => {
  const { open, past } = sortForView(list.value)
  const out = []
  for (const b of BUCKETS) {
    if (b.key === 'past') continue
    const items = open.filter((a) => b.match(dayDiff(parseTime(a.start_time).date)))
    if (items.length) out.push({ key: b.key, label: b.label, items, state: 'open' })
  }
  if (past.length) out.push({ key: 'past', label: '已结束', items: past, state: 'past' })
  return out
})

const emptyTitle = computed(() =>
  applied.value.status === 'upcoming' && !applied.value.keyword && !applied.value.startDate && !applied.value.endDate
    ? '这几天还没有人开球局'
    : '没有符合条件的球局'
)

onMounted(() => fetchPage(1))
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">球局</h1>
        <p class="page-sub">
          找一场能去的球，或者自己开一局
          <template v-if="!loading && total"> · 共 {{ total }} 场</template>
        </p>
      </div>

      <div class="head-actions">
        <AppButton to="/activities/new">发起球局</AppButton>
      </div>
    </div>

    <form class="search-panel panel" role="search" @submit.prevent="search">
      <label class="search-field search-field--keyword">
        <span>活动名称</span>
        <el-input v-model="filters.keyword" placeholder="输入标题关键词" clearable maxlength="100" />
      </label>
      <label class="search-field search-field--status">
        <span>活动状态</span>
        <el-select v-model="filters.status" aria-label="活动状态">
          <el-option label="即将开始" value="upcoming" />
          <el-option label="全部" value="all" />
          <el-option label="已发布" value="published" />
          <el-option label="已结束" value="ended" />
        </el-select>
      </label>
      <label class="search-field search-field--date">
        <span>活动日期（从）</span>
        <el-date-picker v-model="filters.startDate" type="date" value-format="YYYY-MM-DD" placeholder="不限" clearable />
      </label>
      <label class="search-field search-field--date">
        <span>活动日期（至）</span>
        <el-date-picker v-model="filters.endDate" type="date" value-format="YYYY-MM-DD" placeholder="不限" clearable />
      </label>
      <div class="search-actions">
        <AppButton variant="outline" native-type="submit">查询</AppButton>
        <AppButton variant="ghost" @click="resetSearch">重置</AppButton>
      </div>
    </form>

    <!-- 首屏骨架：形状与真实内容一致，避免"先空后跳" -->
    <div v-if="loading" class="panel">
      <div v-for="i in 4" :key="i" class="row-skel">
        <AppSkeleton height="52px" width="84px" radius="var(--r-sm)" />
        <div class="row-skel__mid">
          <AppSkeleton height="16px" width="46%" />
          <AppSkeleton height="13px" width="28%" style="margin-top: 8px" />
        </div>
        <AppSkeleton height="30px" width="56px" radius="var(--r-sm)" />
      </div>
    </div>

    <template v-else-if="groups.length">
      <section v-for="g in groups" :key="g.key" class="sec">
        <div class="section-head">
          <h2 class="h2">{{ g.label }}</h2>
          <span class="meta tnum">{{ g.items.length }} 场</span>
        </div>
        <div class="panel">
          <ActivityRow
            v-for="a in g.items"
            :key="a.id"
            :activity="a"
            :state="g.state"
          />
        </div>
      </section>

      <div v-if="hasMore" class="more">
        <AppButton variant="outline" :loading="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中' : `加载更多（还有 ${total - list.length} 场）` }}
        </AppButton>
      </div>
    </template>

    <EmptyState
      v-else
      :title="emptyTitle"
      :description="
        applied.status === 'upcoming' && !applied.keyword && !applied.startDate && !applied.endDate
          ? '你可以第一个发起 —— 场地会被立即锁定，审核通过后其他球友就能报名。'
          : '试试调整关键词、状态或日期范围。'
      "
    >
      <AppButton to="/activities/new">发起球局</AppButton>
      <AppButton variant="outline" to="/venues">去预约场地</AppButton>
    </EmptyState>

  </div>
</template>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: var(--s3);
  flex-wrap: wrap;
}

.search-panel {
  display: grid;
  grid-template-columns: minmax(180px, 2fr) minmax(130px, 1fr) repeat(2, minmax(155px, 1fr)) auto;
  gap: var(--s3);
  align-items: end;
  padding: var(--s4);
  margin-bottom: var(--s5);
}

.search-field {
  display: flex;
  flex-direction: column;
  gap: var(--s2);
  min-width: 0;
  font-size: var(--fs-sm);
  color: var(--ink-2);
}

.search-field :deep(.el-date-editor),
.search-field :deep(.el-select) {
  width: 100%;
}

.search-field :deep(.el-date-editor) {
  height: 42px;
}

.search-field :deep(.el-input__wrapper),
.search-field :deep(.el-select__wrapper) {
  min-height: 42px;
  box-sizing: border-box;
}

.search-actions {
  display: flex;
  gap: var(--s2);
}

.sec + .sec {
  margin-top: var(--s6);
}

/* 骨架行：列宽与 ActivityRow 对齐（84 / 1fr / auto） */
.row-skel {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) auto;
  gap: var(--s5);
  align-items: center;
  padding: var(--s4) var(--s5);
}

.row-skel + .row-skel {
  border-top: 1px solid var(--line);
}

.row-skel__mid {
  min-width: 0;
}

.more {
  display: flex;
  justify-content: center;
  margin-top: var(--s6);
}

@media (max-width: 1100px) {
  .search-panel {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-actions {
    align-self: end;
  }
}

@media (max-width: 640px) {
  .search-panel {
    grid-template-columns: minmax(0, 1fr);
  }

  .page-head {
    align-items: stretch;
  }

  .head-actions {
    justify-content: space-between;
  }

  .head-actions :deep(.app-btn) {
    flex: 1;
  }
}
</style>
