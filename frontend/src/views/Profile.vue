<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { updateMe } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const formRef = ref()
const saving = ref(false)
const form = reactive({ nickname: '', avatarUrl: '' })

const rules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 64, message: '不能超过 64 个字符', trigger: 'blur' }
  ],
  avatarUrl: [{ max: 255, message: '地址过长', trigger: 'blur' }]
}

function fill() {
  form.nickname = store.user?.nickname || ''
  form.avatarUrl = store.user?.avatar_url || ''
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    // 只提交填写了的字段：后端语义是「不提供 = 保留原值」
    const payload = { nickname: form.nickname }
    if (form.avatarUrl) payload.avatar_url = form.avatarUrl

    await updateMe(payload)
    await store.fetchMe()
    fill()
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(fill)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">个人资料</h2>
        <p class="page-sub">修改展示给其他球友的昵称和头像</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="profile-card">
          <div class="profile-head">
            <el-avatar :size="72" :src="store.user?.avatar_url">
              {{ (store.user?.nickname || '?').slice(0, 1) }}
            </el-avatar>
            <div class="profile-name">{{ store.user?.nickname }}</div>
            <el-tag :type="store.user?.role === 1 ? 'warning' : 'info'" size="small" effect="light">
              {{ store.user?.role === 1 ? '管理员' : '普通用户' }}
            </el-tag>
          </div>

          <el-descriptions :column="1" border class="profile-desc">
            <el-descriptions-item label="用户名">
              {{ store.user?.username }}
            </el-descriptions-item>
            <el-descriptions-item label="ID">{{ store.user?.id }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">
              {{ store.user?.create_time }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="16">
        <el-card shadow="never" class="profile-card">
          <template #header><span class="card-title">编辑资料</span></template>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" maxlength="64" show-word-limit />
            </el-form-item>

            <el-form-item label="头像地址" prop="avatarUrl">
              <el-input
                v-model="form.avatarUrl"
                placeholder="图片链接，留空则保留当前头像"
              />
              <span class="hint">本项目 MVP 不做文件上传，直接填一个图片 URL 即可</span>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving" @click="save">保存</el-button>
              <el-button @click="fill">重置</el-button>
            </el-form-item>
          </el-form>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="说明：只填昵称时，头像保持不变（后端语义为「不提供的字段不改动」）。"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.profile-card {
  border-radius: 10px;
  margin-bottom: 16px;
}

.profile-head {
  text-align: center;
  padding-bottom: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.profile-name {
  font-size: 16px;
  font-weight: 600;
}

.profile-desc {
  margin-top: 4px;
}

.card-title {
  font-weight: 600;
}

.hint {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>
