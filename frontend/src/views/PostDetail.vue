<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPost,
  deletePost,
  createComment,
  listComments,
  likePost,
  unlikePost
} from '@/api/community'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const id = Number(route.params.id)
const loading = ref(false)
const post = ref(null)
const comments = ref([])
const commentTotal = ref(0)
const commentText = ref('')
const acting = ref(false)

const isAuthor = computed(
  () => post.value && store.user && post.value.user_id === store.user.id
)

async function load() {
  loading.value = true
  try {
    post.value = await getPost(id)
    const res = await listComments(id, { page: 1, page_size: 100 })
    comments.value = res.list || []
    commentTotal.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function toggleLike() {
  if (acting.value) return
  acting.value = true
  try {
    if (post.value.liked) {
      await unlikePost(id)
      post.value.liked = false
      post.value.like_count = Math.max(0, post.value.like_count - 1)
    } else {
      await likePost(id)
      post.value.liked = true
      post.value.like_count += 1
    }
  } finally {
    acting.value = false
  }
}

async function submitComment() {
  const text = commentText.value.trim()
  if (!text) {
    ElMessage.warning('请输入评论内容')
    return
  }
  acting.value = true
  try {
    await createComment(id, { content: text })
    commentText.value = ''
    ElMessage.success('评论成功')
    await load()
  } finally {
    acting.value = false
  }
}

async function doDelete() {
  await ElMessageBox.confirm('删除后无法恢复，评论和点赞也会一并清除。', '删除帖子', {
    type: 'warning',
    confirmButtonText: '确认删除'
  })
  await deletePost(id)
  ElMessage.success('已删除')
  router.push('/community')
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <template v-if="post">
      <div class="page-head">
        <el-button @click="router.push('/community')">
          <el-icon><ArrowLeft /></el-icon>返回社区
        </el-button>
        <el-button v-if="isAuthor" type="danger" plain @click="doDelete">
          <el-icon><Delete /></el-icon>删除帖子
        </el-button>
      </div>

      <el-card shadow="never" class="post-card">
        <h1 class="post-title">{{ post.title }}</h1>

        <div class="post-meta">
          <el-avatar :size="34" :src="post.author_avatar_url">
            {{ (post.author_nickname || '?').slice(0, 1) }}
          </el-avatar>
          <div>
            <div class="author">{{ post.author_nickname }}</div>
            <div class="muted">{{ post.create_time }}</div>
          </div>
          <div class="spacer" />
          <el-button
            :type="post.liked ? 'primary' : 'default'"
            :loading="acting"
            round
            @click="toggleLike"
          >
            <el-icon><Star /></el-icon>
            {{ post.liked ? '已赞' : '点赞' }} {{ post.like_count }}
          </el-button>
        </div>

        <div class="post-body">{{ post.content }}</div>
      </el-card>

      <el-card shadow="never" class="comment-card">
        <template #header>
          <span class="card-title">评论（{{ commentTotal }}）</span>
        </template>

        <div class="comment-editor">
          <el-input
            v-model="commentText"
            type="textarea"
            :rows="2"
            placeholder="说点什么…"
            maxlength="1000"
            show-word-limit
          />
          <el-button type="primary" :loading="acting" @click="submitComment">发表评论</el-button>
        </div>

        <el-divider />

        <div v-if="comments.length" class="comment-list">
          <div v-for="c in comments" :key="c.id" class="comment">
            <el-avatar :size="30" :src="c.avatar_url">
              {{ (c.nickname || '?').slice(0, 1) }}
            </el-avatar>
            <div class="comment-body">
              <div class="comment-head">
                <span class="comment-name">{{ c.nickname }}</span>
                <span class="muted">{{ c.create_time }}</span>
              </div>
              <div class="comment-text">{{ c.content }}</div>
            </div>
          </div>
        </div>
        <el-empty v-else description="还没有评论，来说第一句" :image-size="60" />
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.post-card,
.comment-card {
  border-radius: 10px;
  margin-bottom: 16px;
}

.post-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 16px;
  line-height: 1.4;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.author {
  font-size: 14px;
  font-weight: 500;
}

.spacer {
  flex: 1;
}

.post-body {
  font-size: 15px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.card-title {
  font-weight: 600;
}

.comment-editor {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.comment-editor :deep(.el-textarea) {
  flex: 1;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.comment {
  display: flex;
  gap: 10px;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.comment-name {
  font-size: 13px;
  font-weight: 500;
}

.comment-text {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
