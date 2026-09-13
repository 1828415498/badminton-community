<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listActivities } from '@/api/activity'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(9)
const onlyFuture = ref(true)

function statusTag(status) {
  const map = {
    0: { type: 'warning', text: '待审核' },
    1: { type: 'success', text: '报名中' },
    2: { type: 'danger', text: '已驳回' },
    3: { type: 'info', text: '已取消' },
    4: { type: '', text: '已结束' }
  }
  return map[status] || { type: '', text: '未知' }
}

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (onlyFuture.value) {
      params.start_from = new Date().toISOString().slice(0, 19)
    }
    const res = await listActivities(params)
    list.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">活动</h2>
        <p class="page-sub">找一个球局加入，或者自己发起一场</p>
      </div>
      <div class="head-actions">
        <el-switch
          v-model="onlyFuture"
          active-text="只看未来"
          @change="(page = 1), load()"
        />
        <el-button type="primary" @click="router.push('/activities/new')">
          <el-icon><Plus /></el-icon>发起活动
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="card-grid">
      <el-card
        v-for="a in list"
        :key="a.id"
        shadow="hover"
        class="act-card"
        @click="router.push(`/activities/${a.id}`)"
      >
        <div class="act-top">
          <span class="act-title">{{ a.title }}</span>
          <el-tag :type="statusTag(a.status).type" size="small" effect="light">
            {{ statusTag(a.status).text }}
          </el-tag>
        </div>

        <div class="act-meta">
          <div><el-icon><Clock /></el-icon>{{ a.start_time }} ~ {{ a.end_time.slice(11) }}</div>
          <div><el-icon><User /></el-icon>{{ a.creator_nickname }}</div>
        </div>

        <div class="act-foot">
          <el-progress
            :percentage="Math.min(100, Math.round((a.joined_count / a.max_members) * 100))"
            :stroke-width="6"
            :show-text="false"
          />
          <span class="act-count">
            {{ a.joined_count }} / {{ a.max_members }} 人
          </span>
        </div>
      </el-card>
    </div>

    <el-empty v-if="!loading && !list.length" description="暂时没有活动，要不要发起一个？" />

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="load"
      />
    </div>
  </div>
</template>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.act-card {
  cursor: pointer;
  border-radius: 10px;
}

.act-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.act-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}

.act-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 14px;
}

.act-meta > div {
  display: flex;
  align-items: center;
  gap: 6px;
}

.act-foot {
  display: flex;
  align-items: center;
  gap: 10px;
}

.act-foot :deep(.el-progress) {
  flex: 1;
}

.act-count {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
