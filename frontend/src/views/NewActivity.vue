<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createActivity } from '@/api/activity'
import { listVenues, listCourts } from '@/api/venue'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const courts = ref([])

const form = reactive({
  title: '',
  description: '',
  startTime: '',
  endTime: '',
  maxMembers: 4,
  courtIds: []
})

const rules = {
  title: [
    { required: true, message: '请输入活动标题', trigger: 'blur' },
    { max: 100, message: '不能超过 100 个字符', trigger: 'blur' }
  ],
  description: [{ max: 2000, message: '不能超过 2000 个字符', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  maxMembers: [{ required: true, message: '请填写人数上限', trigger: 'blur' }],
  courtIds: [
    {
      type: 'array',
      required: true,
      min: 1,
      message: '至少选择一块球场',
      trigger: 'change'
    }
  ]
}

const startHourOptions = computed(() => {
  if (!form.startTime || !form.endTime) return []
  return []
})

// 结束时间必须晚于开始时间
function validateEnd() {
  if (form.startTime && form.endTime && form.endTime <= form.startTime) {
    ElMessage.warning('结束时间必须晚于开始时间')
    form.endTime = ''
  }
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const sameDay = form.startTime.slice(0, 10) === form.endTime.slice(0, 10)
  if (!sameDay) {
    ElMessage.warning('单个活动不能跨天')
    return
  }

  await ElMessageBox.confirm(
    '提交后活动进入「待审核」，但所选球场会立即被锁定（其他人在此期间约不到）。审核通过后才会开放报名。',
    '确认发起活动',
    { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '再改改' }
  )

  submitting.value = true
  try {
    const created = await createActivity({
      title: form.title,
      description: form.description || null,
      start_time: form.startTime,
      end_time: form.endTime,
      max_members: Number(form.maxMembers),
      court_ids: form.courtIds
    })
    ElMessage.success('活动已提交，等待管理员审核')
    router.push(`/activities/${created.id}`)
  } catch (e) {
    // 冲突 / 超出窗口等错误由拦截器提示
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const venues = await listVenues()
  if (venues.length) {
    courts.value = await listCourts(venues[0].id, { status: 1 })
  }
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">发起活动</h2>
        <p class="page-sub">选好时间和场地，叫上球友一起打</p>
      </div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-alert
      class="tip"
      type="warning"
      :closable="false"
      show-icon
      title="注意：提交后所选球场会立即被锁定，即使活动还在待审核。审核驳回或取消后场地会自动释放。"
    />

    <el-card shadow="never" class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="活动标题" prop="title">
          <el-input v-model="form.title" placeholder="例如：周五晚 7-9 点约战" maxlength="100" show-word-limit />
        </el-form-item>

        <el-form-item label="活动描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="水平要求、收费方式、联系方式等（选填）"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            placeholder="选择开始时间（整点）"
            format="YYYY-MM-DD HH:00"
            value-format="YYYY-MM-DDTHH:00:00"
            :disabled-date="(d) => d < new Date(new Date().toDateString())"
            @change="validateEnd"
          />
          <span class="hint">时间会按整点对齐</span>
        </el-form-item>

        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            placeholder="选择结束时间（整点）"
            format="YYYY-MM-DD HH:00"
            value-format="YYYY-MM-DDTHH:00:00"
            :disabled-date="(d) => d < new Date(new Date().toDateString())"
          />
          <span class="hint">必须与开始时间同一天</span>
        </el-form-item>

        <el-form-item label="人数上限" prop="maxMembers">
          <el-input-number v-model="form.maxMembers" :min="1" :max="50" />
          <span class="hint">你自己会自动占用 1 个名额</span>
        </el-form-item>

        <el-form-item label="选择球场" prop="courtIds">
          <el-checkbox-group v-model="form.courtIds" class="court-checks">
            <el-checkbox v-for="c in courts" :key="c.id" :value="c.id" border>
              {{ c.name }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">
            提交活动
          </el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.tip {
  margin-bottom: 16px;
}

.form-card {
  border-radius: 10px;
  max-width: 720px;
}

.hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.court-checks {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.court-checks :deep(.el-checkbox) {
  margin-right: 0;
}
</style>
