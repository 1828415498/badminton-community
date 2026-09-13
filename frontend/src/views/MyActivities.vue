<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { myActivities, myRegistrations } from '@/api/activity'

const router = useRouter()
const tab = ref('created')

const loading = ref(false)
const created = ref([])
const joined = ref([])

// 我发起的：按状态筛选
const createdStatus = ref(null)

async function loadCreated() {
  const params = { page: 1, page_size: 50 }
  if (createdStatus.value !== null) params.status = createdStatus.value
  const res = await myActivities(params)
  created.value = res.list || []
}

async function loadJoined() {
  // status 缺省时后端只返回「已报名」；这里显式查有效报名
  const res = await myRegistrations({ page: 1, page_size: 50, status: 1 })
  joined.value = res.list || []
}

async function load() {
  loading.value = true
  try {
    if (tab.value === 'created') await loadCreated()
    else await loadJoined()
  } finally {
    loading.value = false
  }
}

function statusTag(status) {
  const map = {
    0: { type: 'warning', text: '待审核' },
    1: { type: 'success', text: '报名中' },
    2: { type: 'danger', text: '已驳回' },
    3: { type: 'info', text: '已取消' },
    4: { type: '', text: '已结束' }
  }
  return map[status] || { type: '', text: '' }
}

watch(tab, load)
watch(createdStatus, loadCreated)

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">我的活动</h2>
        <p class="page-sub">我发起的，以及我报名参加的</p>
      </div>
      <el-button type="primary" @click="router.push('/activities/new')">
        <el-icon><Plus /></el-icon>发起活动
      </el-button>
    </div>

    <el-tabs v-model="tab" class="tabs">
      <el-tab-pane label="我发起的" name="created">
        <div class="filter-row">
          <el-radio-group v-model="createdStatus">
            <el-radio-button :value="null">全部</el-radio-button>
            <el-radio-button :value="0">待审核</el-radio-button>
            <el-radio-button :value="1">报名中</el-radio-button>
            <el-radio-button :value="2">已驳回</el-radio-button>
            <el-radio-button :value="3">已取消</el-radio-button>
            <el-radio-button :value="4">已结束</el-radio-button>
          </el-radio-group>
        </div>

        <el-table
          v-loading="loading"
          :data="created"
          empty-text="你还没有发起过活动"
          @row-click="(row) => router.push(`/activities/${row.id}`)"
        >
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column label="时间" min-width="200">
            <template #default="{ row }">
              {{ row.start_time }} ~ {{ row.end_time.slice(11) }}
            </template>
          </el-table-column>
          <el-table-column label="报名" width="110">
            <template #default="{ row }">
              {{ row.joined_count }} / {{ row.max_members }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status).type" size="small" effect="light">
                {{ statusTag(row.status).text }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我参加的" name="joined">
        <el-table
          v-loading="loading"
          :data="joined"
          empty-text="你还没有报名任何活动"
          @row-click="(row) => router.push(`/activities/${row.activity.id}`)"
        >
          <el-table-column label="标题" min-width="180">
            <template #default="{ row }">{{ row.activity.title }}</template>
          </el-table-column>
          <el-table-column label="时间" min-width="200">
            <template #default="{ row }">
              {{ row.activity.start_time }} ~ {{ row.activity.end_time.slice(11) }}
            </template>
          </el-table-column>
          <el-table-column label="发起人" width="120">
            <template #default="{ row }">{{ row.activity.creator_nickname }}</template>
          </el-table-column>
          <el-table-column label="报名" width="100">
            <template #default="{ row }">
              {{ row.activity.joined_count }} / {{ row.activity.max_members }}
            </template>
          </el-table-column>
          <el-table-column label="活动状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.activity.status).type" size="small" effect="light">
                {{ statusTag(row.activity.status).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="报名时间" min-width="170">
            <template #default="{ row }">{{ row.registration.create_time }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.tabs {
  background: #fff;
  border-radius: 10px;
  padding: 8px 16px 16px;
}

.filter-row {
  margin-bottom: 14px;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
