<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPendingActivities,
  approveActivity,
  rejectActivity,
  createCourt,
  renameCourt,
  disableCourt,
  enableCourt
} from '@/api/admin'
import { listVenues, listCourts } from '@/api/venue'

/**
 * Admin —— 管理台。
 *
 * 存在的理由：活动提交后进入「待审核」，报名要等审核通过才开放。若没有这一页，
 * 界面上的状态流转就断在第一步 —— 现场发起一场球局后，没人能把它变成"报名中"。
 *
 * 这里调的全部是已有接口，后端零改动；真正的权限判定在 Service 层
 * （前端只在路由上挡一道，属于体验而非安全边界）。
 */

const loading = ref(false)
const pending = ref([])
const courts = ref([])
const venueId = ref(null)

/** 场地 id → 名称。待审核列表只下发 court_ids，不 join 场地名 */
function courtLabel(ids) {
  if (!ids || !ids.length) return '—'
  return ids
    .map((id) => courts.value.find((c) => c.id === id)?.name || `#${id}`)
    .join('、')
}

async function loadPending() {
  const res = await listPendingActivities({ page: 1, page_size: 50 })
  pending.value = res.list || []
}

async function loadCourts() {
  const venues = await listVenues()
  if (!venues.length) return
  venueId.value = venues[0].id
  // 不传 status：管理端要看到已停用的场地，否则无法演示"启用"
  courts.value = await listCourts(venueId.value)
}

async function load() {
  loading.value = true
  try {
    await Promise.all([loadPending(), loadCourts()])
  } finally {
    loading.value = false
  }
}

/**
 * ElMessageBox 用 reject 表达"用户点了取消"，这不是异常。
 * 不接住它就会变成一条未处理的 Promise 拒绝 —— 所以每个 await 都包 try/catch。
 */

async function doApprove(row) {
  try {
    await ElMessageBox.confirm(
      `通过后《${row.title}》会立即开放报名，已锁定的场地保持不变。`,
      '审核通过',
      { type: 'warning', confirmButtonText: '确认通过', cancelButtonText: '再看看' }
    )
  } catch (e) {
    return
  }
  await approveActivity(row.id)
  ElMessage.success('已通过，球局已开放报名')
  loadPending()
}

async function doReject(row) {
  let reason
  try {
    const res = await ElMessageBox.prompt(
      '驳回原因会展示给发起人，不能留空。',
      `驳回《${row.title}》`,
      {
        inputPlaceholder: '例如：该时段场地已被占用',
        inputValidator: (v) => (v && v.trim() ? true : '必须填写驳回原因'),
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消'
      }
    )
    reason = res.value
  } catch (e) {
    return
  }
  await rejectActivity(row.id, reason.trim())
  ElMessage.success('已驳回，场地已释放')
  loadPending()
}

async function doCreateCourt() {
  if (!venueId.value) return
  let name
  try {
    const res = await ElMessageBox.prompt('新球场的名称（不超过 32 字）', '新增球场', {
      inputPlaceholder: '例如：7 号场',
      inputValidator: (v) => (v && v.trim() ? true : '名称不能为空'),
      confirmButtonText: '创建'
    })
    name = res.value
  } catch (e) {
    return
  }
  await createCourt(venueId.value, name.trim())
  ElMessage.success('球场已创建，默认可预约')
  loadCourts()
}

async function doRenameCourt(row) {
  let name
  try {
    const res = await ElMessageBox.prompt('新的球场名称', '修改球场名', {
      inputValue: row.name,
      inputValidator: (v) => (v && v.trim() ? true : '名称不能为空'),
      confirmButtonText: '保存'
    })
    name = res.value
  } catch (e) {
    return
  }
  if (name.trim() === row.name) return
  await renameCourt(row.id, name.trim())
  ElMessage.success('已改名')
  loadCourts()
}

async function doToggleCourt(row) {
  const turningOff = row.status === 1
  try {
    await ElMessageBox.confirm(
      turningOff
        ? `停用「${row.name}」后，该场地不再接受新的预约。若它还有未来的有效预约，后端会拒绝这次停用。`
        : `恢复「${row.name}」为可预约状态？`,
      turningOff ? '停用球场' : '启用球场',
      { type: 'warning', confirmButtonText: turningOff ? '确认停用' : '确认启用' }
    )
  } catch (e) {
    return
  }
  if (turningOff) await disableCourt(row.id)
  else await enableCourt(row.id)
  ElMessage.success(turningOff ? '已停用' : '已恢复可预约')
  loadCourts()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">管理台</h2>
        <p class="page-sub">审核球局、维护球场 —— 仅管理员可见</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <!-- ---------- 待审核球局 ---------- -->
    <section class="panel">
      <div class="panel-head">
        <h3 class="panel-title">待审核球局</h3>
        <span class="panel-meta tnum">{{ pending.length }} 条</span>
      </div>

      <p class="panel-hint">
        发起人提交后，所选场地<b>立即锁定</b>（其他人约不到），但要审核通过才开放报名。
        驳回会释放场地并作废报名。
      </p>

      <el-table v-loading="loading" :data="pending" empty-text="没有待审核的球局">
        <el-table-column prop="title" label="标题" min-width="150" />
        <el-table-column prop="creator_nickname" label="发起人" width="100" />
        <el-table-column label="时间" min-width="190">
          <template #default="{ row }">
            <span class="tnum">{{ row.start_time }} ~ {{ row.end_time.slice(11) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="场地" min-width="120">
          <template #default="{ row }">{{ courtLabel(row.court_ids) }}</template>
        </el-table-column>
        <el-table-column label="人数上限" width="90">
          <template #default="{ row }">
            <span class="tnum">{{ row.max_members }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="doApprove(row)">通过</el-button>
            <el-button type="danger" link @click="doReject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ---------- 球场管理 ---------- -->
    <section class="panel">
      <div class="panel-head">
        <h3 class="panel-title">球场管理</h3>
        <el-button type="primary" size="small" @click="doCreateCourt">
          <el-icon><Plus /></el-icon>新增球场
        </el-button>
      </div>

      <p class="panel-hint">
        停用前会检查该场地是否还有未来的有效预约 —— 有则拒绝，避免把已经订出去的场次弄丢。
      </p>

      <el-table v-loading="loading" :data="courts" empty-text="暂无球场">
        <el-table-column prop="name" label="球场" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 1 ? '可预约' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="right">
          <template #default="{ row }">
            <el-button link @click="doRenameCourt(row)">改名</el-button>
            <el-button v-if="row.status === 1" type="danger" link @click="doToggleCourt(row)">
              停用
            </el-button>
            <el-button v-else type="primary" link @click="doToggleCourt(row)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.panel {
  background: var(--surface, #fff);
  border: 1px solid var(--line, #e4e2da);
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 18px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
}

.panel-meta {
  font-size: 13px;
  color: var(--ink-3, #77776f);
}

.panel-hint {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-3, #77776f);
  margin: 0 0 12px;
}

.panel-hint b {
  color: var(--ink-2, #4a4a45);
}
</style>
