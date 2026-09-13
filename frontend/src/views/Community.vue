<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listPosts, createPost } from '@/api/community'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ title: '', content: '' })

const rules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 200, message: '不能超过 200 个字符', trigger: 'blur' }
  ],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await listPosts({ page: page.value, page_size: pageSize.value })
    list.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createPost({ title: form.title, content: form.content })
    ElMessage.success('发布成功')
    dialogVisible.value = false
    form.title = ''
    form.content = ''
    page.value = 1
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">社区</h2>
        <p class="page-sub">聊聊球技、约球心得、场地体验</p>
      </div>
      <el-button type="primary" @click="dialogVisible = true">
        <el-icon><EditPen /></el-icon>发帖
      </el-button>
    </div>

    <div v-loading="loading" class="post-list">
      <el-card
        v-for="p in list"
        :key="p.id"
        shadow="hover"
        class="post-card"
        @click="router.push(`/community/${p.id}`)"
      >
        <div class="post-head">
          <el-avatar :size="34" :src="p.author_avatar_url">
            {{ (p.author_nickname || '?').slice(0, 1) }}
          </el-avatar>
          <div class="post-head-text">
            <div class="post-title">{{ p.title }}</div>
            <div class="muted">
              {{ p.author_nickname }} · {{ p.create_time }}
            </div>
          </div>
        </div>

        <p class="post-content">{{ p.content }}</p>

        <div class="post-foot">
          <span class="stat">
            <el-icon><Star /></el-icon>{{ p.like_count }}
          </span>
          <span class="stat">
            <el-icon><ChatDotRound /></el-icon>{{ p.comment_count }}
          </span>
        </div>
      </el-card>
    </div>

    <el-empty v-if="!loading && !list.length" description="还没有帖子，来发第一帖吧" />

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="发布新帖" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="一句话说清楚你想聊什么" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="7" placeholder="展开说说…" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.post-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.post-card {
  cursor: pointer;
  border-radius: 10px;
}

.post-head {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.post-head-text {
  min-width: 0;
}

.post-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 2px;
}

.post-content {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.6;
  color: #606266;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-foot {
  display: flex;
  gap: 18px;
  font-size: 13px;
  color: #909399;
}

.stat {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
