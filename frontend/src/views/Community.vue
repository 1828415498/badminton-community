<script setup>
/**
 * Community —— 社区。
 *
 * <h2>改造前的问题</h2>
 * 每篇帖子一张 `el-card shadow="hover"`，十张卡摞在一起 —— 正是"Card + Card + Card"。
 * 时间直接渲染成后端原串（`2026-09-13 21:59:29`），点赞用 `Star` 图标（语义也不对，
 * 收藏才用星）。空状态是 Element 默认插画，底部分页器整条都像运营后台。
 *
 * <h2>改造后的结构</h2>
 * 一个面板 + 按时间倒序的**动态流**：谁、什么时候、说了什么、多少人回应。
 * 行之间用发丝线分隔，不再一篇一张卡。
 *
 * <h2>四个刻意不做的功能</h2>
 * <ol>
 *   <li><b>不做「热门 / 推荐」。</b> 后端列表是 `ORDER BY create_time DESC`，
 *       只按创建时间排。在前端对"当前这一页"按点赞数重排，得到的是
 *       "这一页里最热的" —— 和用户理解的"最热"不是一回事，等于撒谎。</li>
 *   <li><b>不做「帖子 + 球局」混合信息流。</b> 球局的列表接口按 `start_time` 排序，
 *       且列表 VO 里没有创建时间 —— 拼不出按时间正确排序的混合流。</li>
 *   <li><b>不加速藏 / 关注 / 转发。</b> 后端没有这些表。为了"像社交平台"而摆上
 *       点不动的按钮，比没有更糟。</li>
 *   <li><b>列表里不显示"我点过赞没有"。</b> `PostItemVO` 没有 `liked` 字段
 *       （只有详情 VO 有）。列表只给总数，"我点过没"归详情页。</li>
 * </ol>
 *
 * <h2>时间用相对表述</h2>
 * 动态流的语境是"刚发生的事"，所以用"3 小时前 / 昨天 21:40"，
 * 而不是"2026-09-13 21:59"。见 `@/utils/datetime` 的 `fromNow`。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listPosts, createPost } from '@/api/community'
import { useUserStore } from '@/stores/user'
import { AppButton, AppInput, AppAvatar, AppSheet, AppSkeleton, EmptyState } from '@/components'
import { fromNow } from '@/utils/datetime'

const store = useUserStore()

const PAGE_SIZE = 10

const loading = ref(true)
const loadingMore = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)

const hasMore = computed(() => list.value.length < total.value)

/**
 * 视图态。
 * 用显式状态而不是 `v-loading` 遮罩：首次加载要给骨架，
 * "一条帖子都没有"要给空状态 + 下一步动作 —— 两件完全不同的事。
 */
const viewState = computed(() => {
  if (loading.value && !list.value.length) return 'loading'
  if (!list.value.length) return 'empty'
  return 'ready'
})

async function fetchPage(append) {
  if (append) loadingMore.value = true
  else loading.value = true
  try {
    const res = await listPosts({ page: page.value, page_size: PAGE_SIZE })
    const rows = res.list || []
    list.value = append ? [...list.value, ...rows] : rows
    total.value = res.total ?? list.value.length
  } catch (e) {
    if (!append) {
      list.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function loadMore() {
  page.value += 1
  fetchPage(true)
}

// ---------- 发帖 ----------
const sheetOpen = ref(false)
const submitting = ref(false)
const form = ref({ title: '', content: '' })
const errors = ref({ title: '', content: '' })

function openSheet() {
  form.value = { title: '', content: '' }
  errors.value = { title: '', content: '' }
  sheetOpen.value = true
}

/** 前端只做"能不能提交"的判断，规则与后端一致（标题/正文非空、标题 ≤200） */
function validate() {
  const t = form.value.title.trim()
  const c = form.value.content.trim()
  errors.value = {
    title: !t ? '写个标题吧' : t.length > 200 ? '标题不能超过 200 字' : '',
    content: !c ? '正文不能为空' : ''
  }
  return !errors.value.title && !errors.value.content
}

async function submit() {
  if (!validate()) return
  submitting.value = true
  try {
    await createPost({ title: form.value.title.trim(), content: form.value.content.trim() })
    ElMessage.success('已发布')
    sheetOpen.value = false
    page.value = 1
    await fetchPage(false)
  } catch (e) {
    // 具体原因由响应拦截器统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => fetchPage(false))
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">社区</h1>
        <p class="page-sub">
          打羽毛球的人在这里聊球技、聊场地、约球友
          <template v-if="!loading && total"> · 共 {{ total }} 条</template>
        </p>
      </div>
      <AppButton @click="openSheet">发帖</AppButton>
    </div>

    <!-- 首屏骨架：行高与真实帖子一致，避免"先空后跳" -->
    <div v-if="viewState === 'loading'" class="panel">
      <div v-for="i in 3" :key="i" class="post-skel">
        <AppSkeleton height="26px" width="26px" radius="50%" />
        <div class="post-skel__body">
          <AppSkeleton height="13px" width="160px" />
          <AppSkeleton height="17px" width="58%" style="margin-top: 8px" />
          <AppSkeleton height="14px" width="86%" style="margin-top: 12px" />
          <AppSkeleton height="14px" width="64%" style="margin-top: 6px" />
        </div>
      </div>
    </div>

    <template v-else-if="viewState === 'ready'">
      <div class="panel">
        <RouterLink
          v-for="p in list"
          :key="p.id"
          class="post"
          :to="`/community/${p.id}`"
        >
          <AppAvatar :size="30" :src="p.author_avatar_url" :name="p.author_nickname" />

          <div class="post__body">
            <!-- 动态流的第一行先回答"谁 · 什么时候" -->
            <p class="post__by">
              <span class="post__who">{{ p.author_nickname }}</span>
              <span class="post__sep" aria-hidden="true"></span>
              <span class="post__when">{{ fromNow(p.create_time) }}</span>
            </p>

            <h2 class="post__title">{{ p.title }}</h2>
            <p class="post__excerpt">{{ p.content }}</p>

            <p class="post__foot">
              <span class="stat" :class="{ 'is-zero': !p.like_count }">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.8-7.5 1.1-1.1a5.5 5.5 0 0 0 0-7.8z" />
                </svg>
                <span class="tnum">{{ p.like_count }}</span>
                <span class="sr-only">人点赞</span>
              </span>
              <span class="stat" :class="{ 'is-zero': !p.comment_count }">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9 9 0 0 1-3.8-.8L3 21l1.9-5a8.4 8.4 0 0 1-.9-4 8.4 8.4 0 0 1 9-8.4 8.4 8.4 0 0 1 8 8.9z" />
                </svg>
                <span class="tnum">{{ p.comment_count }}</span>
                <span class="sr-only">条评论</span>
              </span>
            </p>
          </div>
        </RouterLink>
      </div>

      <div v-if="hasMore" class="more">
        <AppButton variant="outline" :loading="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中' : `加载更多（还有 ${total - list.length} 条）` }}
        </AppButton>
      </div>
    </template>

    <EmptyState
      v-else
      title="社区里还没有人开口"
      description="第一句话留给你 —— 聊聊球技、场地体验，或者找搭子。"
    >
      <AppButton @click="openSheet">发第一帖</AppButton>
    </EmptyState>

    <!-- 发帖：用 AppSheet（自研弹层）而不是 el-dialog，与全站观感一致 -->
    <AppSheet v-model="sheetOpen" title="发帖" width="600px">
      <div class="form">
        <AppInput
          v-model="form.title"
          label="标题"
          placeholder="一句话说清楚你想聊什么"
          :maxlength="200"
          :error="errors.title"
          required
        />
        <AppInput
          v-model="form.content"
          label="正文"
          type="textarea"
          :rows="7"
          placeholder="展开说说…"
          :error="errors.content"
          required
        />
      </div>
      <template #footer>
        <AppButton variant="ghost" @click="sheetOpen = false">取消</AppButton>
        <AppButton :loading="submitting" @click="submit">发布</AppButton>
      </template>
    </AppSheet>
  </div>
</template>

<style scoped>
/* ---- 首屏骨架 ---- */
.post-skel {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: var(--s3);
  padding: var(--s4) var(--s5);
}

.post-skel + .post-skel {
  border-top: 1px solid var(--line);
}

/* ---- 动态流的每一行 ---- */
.post {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: var(--s3);
  padding: var(--s4) var(--s5);
  color: inherit;
  transition: background 0.14s;
}

.post:hover {
  background: var(--surface-2);
}

/* 相邻两行一条发丝线；用 + 而不是给每行加 border-bottom，
   这样单独用一行时不会多出一条悬空的线 */
.post + .post {
  border-top: 1px solid var(--line);
}

.post__body {
  min-width: 0;
}

.post__by {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 4px;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.post__who {
  font-weight: 600;
  color: var(--ink-2);
}

/* 分隔用真圆点而不是「·」字符：它不承载信息，是纯视觉分隔。
   作为文字时它的对比度只有 1.42:1（实测），而它又没有信息量 ——
   与其为了合规把它加深成显眼的文字，不如承认它是装饰。 */
.post__sep {
  flex-shrink: 0;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--line-2);
}

.post__title {
  margin: 0 0 4px;
  font-size: var(--fs-lead);
  font-weight: 600;
  line-height: 1.4;
  color: var(--ink);
}

/* 正文摘要两行截断：列表不该给全文，全文归详情页 */
.post__excerpt {
  margin: 0 0 var(--s3);
  font-size: var(--fs-sm);
  line-height: 1.65;
  color: var(--ink-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post__foot {
  display: flex;
  align-items: center;
  gap: var(--s4);
  margin: 0;
}

.stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-sm);
  color: var(--ink-2);
}

/* 计数为 0 时降级：0 个赞不值得和 12 个赞一样醒目。
   🔴 这里刻意**不用 opacity** —— opacity 会等比压低文字对比度，
   ink-3 本身是达标的 5.0:1，乘 0.7 后就掉到 3.5:1 左右、低于 AA 线。
   用颜色令牌（ink-2 → ink-3）做降级，两者都达标。 */
.stat.is-zero {
  color: var(--ink-3);
}

/* 只给读屏用，视觉上不占位 */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.more {
  display: flex;
  justify-content: center;
  margin-top: var(--s6);
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--s5);
}

@media (max-width: 640px) {
  .page-head {
    align-items: stretch;
  }

  .page-head :deep(.app-btn) {
    flex: 1;
  }
}
</style>
