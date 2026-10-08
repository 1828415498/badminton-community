<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myReservations, cancelReservation } from '@/api/reservation'

const router = useRouter()

const FILTERS = [
  { label: '全部', value: null },
  { label: '有效', value: 1 },
  { label: '已取消', value: 2 }
]

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref(null)

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (status.value !== null) params.status = status.value
    const res = await myReservations(params)
    list.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function setStatus(v) {
  status.value = v
  page.value = 1
  load()
}

async function doCancel(row) {
  await ElMessageBox.confirm(
    `确认取消 ${row.court_name} ${row.start_time} 的预约吗？`,
    '取消预约',
    { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '再想想' }
  )
  await cancelReservation(row.id)
  ElMessage.success('已取消')
  await load()
}

/** 开场前不足 4 小时不允许自行取消（后端也有校验，这里提前给出状态） */
function canCancel(row) {
  if (row.status !== 1) return false
  const start = new Date(row.start_time.replace(/-/g, '/')).getTime()
  return start - Date.now() > 4 * 3600 * 1000
}

function statusOf(row) {
  if (row.status === 2) return { type: 'info', text: '已取消' }
  const start = new Date(row.start_time.replace(/-/g, '/')).getTime()
  if (start + 3600 * 1000 < Date.now()) return { type: 'info', text: '已结束' }
  return { type: 'success', text: '有效' }
}

/** 表格里只显示 时:分，日期已由上一列的时间段隐含，不必重复 */
function hhmm(iso) {
  return String(iso).slice(11, 16)
}

/**
 * 打开预约凭证（报表打印页）。
 *
 * 新标签打开，而不是当前页跳转：打印页会把整页交给浏览器排版，
 * 放在当前标签会打断"列表 → 详情 → 返回"的浏览动线。
 * 是否允许打印由后端判定（本人或管理员），这里只是入口。
 */
function openReceipt(row) {
  const { href } = router.resolve({ name: 'print-reservation', params: { id: row.id } })
  window.open(href, '_blank', 'noopener')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">我的预约</h1>
        <p class="page-sub">
          个人球场预约记录
          <template v-if="total"> · 共 {{ total }} 条</template>
        </p>
      </div>

      <div class="segmented" role="group" aria-label="按状态筛选">
        <button
          v-for="f in FILTERS"
          :key="f.label"
          type="button"
          :aria-pressed="status === f.value"
          @click="setStatus(f.value)"
        >
          {{ f.label }}
        </button>
      </div>
    </div>

    <section class="panel">
      <el-table v-loading="loading" :data="list" :border="false">
        <el-table-column prop="court_name" label="球场" min-width="110" />

        <el-table-column label="时间段" min-width="240">
          <template #default="{ row }">
            <span class="slot tnum">
              {{ String(row.start_time).slice(0, 10) }}
              <strong>{{ hhmm(row.start_time) }}–{{ hhmm(row.end_time) }}</strong>
            </span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="statusOf(row).type" size="small" effect="light" disable-transitions>
              {{ statusOf(row).text }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" min-width="150">
          <template #default="{ row }">
            <span class="muted tnum">{{ row.create_time }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="176" align="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openReceipt(row)">打印凭证</el-button>
            <el-button v-if="canCancel(row)" type="danger" link @click="doCancel(row)">
              取消预约
            </el-button>
            <span v-else-if="row.status === 1" class="muted" title="开场前 4 小时内不可取消">
              已过取消时限
            </span>
          </template>
        </el-table-column>

        <!-- 空状态：说明现状 + 给出下一步动作 -->
        <template #empty>
          <div class="empty">
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
                <rect x="3" y="5" width="18" height="16" rx="2" />
                <path d="M3 10h18M8 3v4M16 3v4" stroke-linecap="round" />
              </svg>
            </div>
            <p class="empty-title">
              {{ status === null ? '还没有预约记录' : '这个状态下没有记录' }}
            </p>
            <p class="empty-sub">去场馆预约页选一块场地和时段就行</p>
            <el-button type="primary" @click="$router.push('/venues')">去预约场地</el-button>
          </div>
        </template>
      </el-table>

      <div v-if="total > pageSize" class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="setStatus(status)"
        />
      </div>
    </section>

    <p class="foot-note">
      活动占场（你自己发起的活动锁定的场地）不在这里，请到「我的活动」查看。
    </p>
  </div>
</template>

<style scoped>
.slot {
  font-size: 13px;
  color: var(--ink-3);
}

.slot strong {
  margin-left: 6px;
  font-weight: 600;
  color: var(--ink);
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
  border-top: 1px solid var(--line);
}

.foot-note {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--ink-3);
}

/* ---- 表格重塑：去灰底表头、去竖线、发丝线分隔 ---- */
:deep(.el-table) {
  font-size: 13px;
  --el-table-border-color: var(--line);
}

:deep(.el-table th.el-table__cell) {
  background: transparent;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-3);
  padding: 12px 0;
}

:deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid var(--line);
  padding: 14px 0;
}

/* Element 在表格底部额外画了一条线，会和最后一行的分隔线叠在一起 */
:deep(.el-table__inner-wrapper::before) {
  display: none;
}

:deep(.el-table tr:last-child td.el-table__cell) {
  border-bottom: 0;
}

:deep(.el-table__body tr:hover > td.el-table__cell) {
  background: var(--surface-2);
}
</style>
