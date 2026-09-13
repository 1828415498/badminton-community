<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getActivity,
  cancelActivity,
  registerActivity,
  cancelRegistration,
  listMembers
} from '@/api/activity'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const id = Number(route.params.id)
const loading = ref(false)
const activity = ref(null)
const members = ref([])
const acting = ref(false)

const isCreator = computed(
  () => activity.value && store.user && activity.value.creator_id === store.user.id
)

const isFull = computed(
  () => activity.value && activity.value.joined_count >= activity.value.max_members
)

const joined = computed(
  () => members.value.some((m) => m.user_id === store.user?.id)
)

const canJoin = computed(() => {
  if (!activity.value) return false
  if (activity.value.status !== 1) return false
  if (new Date(activity.value.start_time.replace(/-/g, '/')) <= new Date()) return false
  return true
})

const statusInfo = computed(() => {
  const map = {
    0: { type: 'warning', text: '待审核' },
    1: { type: 'success', text: '报名中' },
    2: { type: 'danger', text: '已驳回' },
    3: { type: 'info', text: '已取消' },
    4: { type: '', text: '已结束' }
  }
  return map[activity.value?.status] || { type: '', text: '' }
})

async function load() {
  loading.value = true
  try {
    activity.value = await getActivity(id)
    if ([1, 4].includes(activity.value.status) || isCreator.value) {
      const res = await listMembers(id, { page: 1, page_size: 50 })
      members.value = res.list || []
    }
  } finally {
    loading.value = false
  }
}

async function doJoin() {
  acting.value = true
  try {
    await registerActivity(id)
    ElMessage.success('报名成功')
    await load()
  } finally {
    acting.value = false
  }
}

async function doQuit() {
  await ElMessageBox.confirm('确认取消报名吗？名额会释放给其他人。', '取消报名', {
    type: 'warning'
  })
  acting.value = true
  try {
    await cancelRegistration(id)
    ElMessage.success('已取消报名')
    await load()
  } finally {
    acting.value = false
  }
}

async function doCancelActivity() {
  await ElMessageBox.confirm(
    '确认取消这场活动吗？场地会立即释放，所有报名作废。',
    '取消活动',
    { type: 'warning', confirmButtonText: '确认取消活动' }
  )
  acting.value = true
  try {
    await cancelActivity(id)
    ElMessage.success('活动已取消')
    await load()
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <template v-if="activity">
      <div class="page-head">
        <div>
          <h2 class="page-title">{{ activity.title }}</h2>
          <p class="page-sub">
            由 {{ activity.creator_nickname }} 发起
            <el-tag :type="statusInfo.type" size="small" effect="light" class="ml">
              {{ statusInfo.text }}
            </el-tag>
          </p>
        </div>
        <el-button @click="router.push('/activities')">返回列表</el-button>
      </div>

      <el-alert
        v-if="activity.status === 0"
        class="tip"
        type="warning"
        :closable="false"
        show-icon
        title="活动待管理员审核。场地已被锁定，审核通过后才会开放报名。"
      />
      <el-alert
        v-else-if="activity.status === 2"
        class="tip"
        type="error"
        :closable="false"
        show-icon
        :title="`活动已被驳回：${activity.reject_reason || '未填写原因'}`"
      />
      <el-alert
        v-else-if="activity.status === 3"
        class="tip"
        type="info"
        :closable="false"
        show-icon
        title="活动已取消，场地已释放。"
      />

      <el-row :gutter="16">
        <el-col :xs="24" :md="16">
          <el-card shadow="never" class="info-card">
            <template #header><span class="card-title">活动信息</span></template>

            <el-descriptions :column="1" border>
              <el-descriptions-item label="时间">
                {{ activity.start_time }} ~ {{ activity.end_time.slice(11) }}
              </el-descriptions-item>
              <el-descriptions-item label="球场">
                <el-tag
                  v-for="cid in activity.court_ids"
                  :key="cid"
                  size="small"
                  class="court-tag"
                >
                  {{ cid }} 号场
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="名额">
                {{ activity.joined_count }} / {{ activity.max_members }}
                <el-tag v-if="isFull" type="danger" size="small" class="ml">已满</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="描述">
                {{ activity.description || '（无）' }}
              </el-descriptions-item>
              <el-descriptions-item v-if="activity.review_time" label="审核时间">
                {{ activity.review_time }}
              </el-descriptions-item>
            </el-descriptions>

            <div class="actions">
              <el-button
                v-if="canJoin && !joined && !isFull"
                type="primary"
                :loading="acting"
                @click="doJoin"
              >
                报名参加
              </el-button>
              <el-button
                v-else-if="canJoin && joined"
                :loading="acting"
                @click="doQuit"
              >
                取消报名
              </el-button>
              <el-tooltip v-else-if="isFull && !joined" content="名额已满" placement="top">
                <span><el-button disabled>名额已满</el-button></span>
              </el-tooltip>
              <el-tag v-else-if="!canJoin && activity.status === 1" type="info">
                报名已截止
              </el-tag>

              <el-button
                v-if="isCreator && [0, 1].includes(activity.status)"
                type="danger"
                plain
                :loading="acting"
                @click="doCancelActivity"
              >
                取消活动
              </el-button>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="8">
          <el-card shadow="never" class="info-card">
            <template #header>
              <span class="card-title">已报名（{{ members.length }}）</span>
            </template>
            <div v-if="members.length" class="member-list">
              <div v-for="m in members" :key="m.id" class="member">
                <el-avatar :size="30" :src="m.avatar_url">
                  {{ (m.nickname || '?').slice(0, 1) }}
                </el-avatar>
                <span class="member-name">{{ m.nickname }}</span>
                <el-tag
                  v-if="m.user_id === activity.creator_id"
                  size="small"
                  type="success"
                  effect="plain"
                >
                  发起人
                </el-tag>
              </div>
            </div>
            <el-empty v-else description="还没有人报名" :image-size="60" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.tip {
  margin-bottom: 16px;
}

.info-card {
  border-radius: 10px;
  margin-bottom: 16px;
}

.card-title {
  font-weight: 600;
}

.ml {
  margin-left: 8px;
}

.court-tag {
  margin-right: 6px;
}

.actions {
  margin-top: 18px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.member {
  display: flex;
  align-items: center;
  gap: 10px;
}

.member-name {
  flex: 1;
  font-size: 14px;
}
</style>
