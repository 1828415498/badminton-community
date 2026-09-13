<script setup>
/**
 * Home —— 首页 · 球局广场
 *
 * 存在的理由：改造前 `/` 直接 redirect 到场馆排期表，
 * 用户登录后第一眼看到的是数据库视图。首页要做的是回答
 * 「这里有没有我能去的球」——所以它的核心内容按**时间**组织。
 *
 * 关于"附近"：后端 venue 表没有经纬度，算不出距离。
 * 所以发现维度从**空间换成了时间**（今天 / 接下来几天）。
 * 羽毛球本来就是强时间偏好的运动（下班后、周末），这个替换比伪装成"附近"诚实。
 *
 * 关于"热门/推荐"：后端没有热度信号（只有 joined_count 这类原始计数），
 * 编一个"热门"就是假数据。所以第二块内容用"接下来几天"，并把
 * 「已报 6/8」这类真实填充率做成"快满"标记。
 */
import { ref, computed, onMounted } from 'vue'
import { listActivities } from '@/api/activity'
import { listPosts } from '@/api/community'
import { listVenues, listCourts } from '@/api/venue'
import {
  AppButton,
  AppAvatar,
  AppBadge,
  AppSkeleton,
  EmptyState,
  RallyArc,
  ActivityRow
} from '@/components'

const loading = ref(true)

const todayActs = ref([])
const todayTotal = ref(0)
const upcomingActs = ref([])
const weekTotal = ref(0)
const posts = ref([])
const venue = ref(null)
const courtCount = ref(0)

// 时间工具统一从 @/utils/datetime 取（原先这里和 ActivityRow 各有一份副本）
import { startOf, endOf, iso, sortByStartAsc, whenShort } from '@/utils/datetime'

function fillRatio(a) {
  if (!a.max_members) return 0
  return a.joined_count / a.max_members
}

// ---------- 数据 ----------
/**
 * 用 allSettled 而不是 all：首页由几块互相独立的区块拼成，
 * 社区接口挂了不该让球局区块也变空白。
 */
async function load() {
  loading.value = true
  try {
    const [t, u, w, p, vs] = await Promise.allSettled([
      listActivities({
        status: 1,
        start_from: iso(startOf(0)),
        start_to: iso(endOf(0)),
        page: 1,
        page_size: 4
      }),
      listActivities({
        status: 1,
        start_from: iso(startOf(1)),
        page: 1,
        page_size: 4
      }),
      listActivities({
        status: 1,
        start_from: iso(startOf(0)),
        start_to: iso(endOf(7)),
        page: 1,
        page_size: 1
      }),
      listPosts({ page: 1, page_size: 3 }),
      listVenues()
    ])

    if (t.status === 'fulfilled') {
      todayActs.value = sortByStartAsc(t.value.list)
      todayTotal.value = t.value.total ?? todayActs.value.length
    }
    if (u.status === 'fulfilled') upcomingActs.value = sortByStartAsc(u.value.list)
    if (w.status === 'fulfilled') weekTotal.value = w.value.total ?? 0
    if (p.status === 'fulfilled') posts.value = p.value.list || []

    const venues = vs.status === 'fulfilled' ? vs.value || [] : []
    if (venues.length) {
      venue.value = venues[0]
      try {
        const courts = await listCourts(venue.value.id, { status: 1 })
        courtCount.value = courts?.length ?? 0
      } catch (e) {
        /* 场地数量拿不到不影响其它区块 */
      }
    }
  } finally {
    loading.value = false
  }
}

/** 第一屏那句"活着的"文案 —— 用真实数字，不用"欢迎来到"这种空话 */
const liveLine = computed(() => {
  if (loading.value) return ''
  if (todayTotal.value > 0) return `今天已经有 ${todayTotal.value} 场球局`
  return '今天还没有人开球局，你可以第一个发起'
})

const statsParts = computed(() => {
  const parts = []
  if (weekTotal.value > 0) parts.push(`未来 7 天共 ${weekTotal.value} 场`)
  if (courtCount.value > 0) parts.push(`${venue.value?.name ?? ''}有 ${courtCount.value} 片场地`)
  return parts
})

function excerpt(s, n = 46) {
  const t = String(s || '').replace(/\s+/g, ' ').trim()
  return t.length > n ? t.slice(0, n) + '…' : t
}

onMounted(load)
</script>

<template>
  <div class="home">
    <!-- ===== Hero ===== -->
    <section class="hero">
      <h1 class="display hero__title">FIND YOUR GAME.</h1>
      <p class="lead hero__lead">找到下一场属于你的球局，认识常来这个球馆的人。</p>

      <div class="hero__cta">
        <AppButton size="lg" to="/activities">看看今天的球局</AppButton>
        <AppButton size="lg" variant="outline" to="/activities/new">发起球局</AppButton>
      </div>

      <p class="hero__live">
        {{ liveLine }}
        <template v-if="statsParts.length">
          <span class="hero__sep">·</span>{{ statsParts.join(' · ') }}
        </template>
      </p>
    </section>

    <!-- 轨迹弧：品牌母题，作为 Hero 与内容区之间的分隔 -->
    <RallyArc class="hero__arc" tone="brand" />

    <div class="body">
      <!-- ===== 今天开打 ===== -->
      <section class="sec">
        <div class="section-head">
          <h2 class="h2">今天开打</h2>
          <RouterLink class="more" to="/activities">
            全部球局
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M9 6l6 6-6 6" />
            </svg>
          </RouterLink>
        </div>

        <div class="panel">
          <template v-if="loading">
            <div class="row-skel">
              <AppSkeleton height="52px" width="84px" radius="var(--r-sm)" />
              <div class="row-skel__mid">
                <AppSkeleton height="16px" width="45%" />
                <AppSkeleton height="13px" width="30%" style="margin-top: 8px" />
              </div>
            </div>
            <div class="row-skel">
              <AppSkeleton height="52px" width="84px" radius="var(--r-sm)" />
              <div class="row-skel__mid">
                <AppSkeleton height="16px" width="55%" />
                <AppSkeleton height="13px" width="26%" style="margin-top: 8px" />
              </div>
            </div>
          </template>

          <template v-else-if="todayActs.length">
            <ActivityRow
              v-for="(a, i) in todayActs"
              :key="a.id"
              class="lined"
              :class="{ 'is-first': i === 0 }"
              :activity="a"
            />
          </template>

          <EmptyState
            v-else
            title="今天还没有人开球局"
            description="要不你来发起第一场？选好时间和场地，提交后场地当场锁定。"
          >
            <AppButton variant="primary" to="/activities/new">发起球局</AppButton>
          </EmptyState>
        </div>
      </section>

      <!-- ===== 接下来几天 ===== -->
      <section v-if="loading || upcomingActs.length" class="sec">
        <div class="section-head">
          <h2 class="h2">接下来几天</h2>
          <RouterLink class="more" to="/activities">
            全部球局
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M9 6l6 6-6 6" />
            </svg>
          </RouterLink>
        </div>

        <div class="panel">
          <template v-if="loading">
            <div v-for="i in 3" :key="i" class="mini-skel">
              <AppSkeleton height="14px" width="34%" />
            </div>
          </template>

          <RouterLink
            v-for="(a, i) in upcomingActs"
            :key="a.id"
            class="mini"
            :class="{ 'is-first': i === 0 }"
            :to="`/activities/${a.id}`"
          >
            <span class="mini__when tnum">{{ whenShort(a.start_time) }}</span>
            <span class="mini__title">{{ a.title }}</span>
            <span class="mini__fill">
              <AppBadge v-if="fillRatio(a) >= 0.75 && a.joined_count < a.max_members" kind="pending" :plain="true">
                快满了
              </AppBadge>
              <span class="tnum">{{ a.joined_count }}/{{ a.max_members }}</span>
            </span>
          </RouterLink>
        </div>
      </section>

      <!-- ===== 场地 ===== -->
      <section v-if="venue" class="sec">
        <div class="section-head">
          <h2 class="h2">场地</h2>
        </div>

        <!-- 一条横向信息带，不是卡片墙 —— 只有一处场地时堆卡片没有意义 -->
        <RouterLink class="band" to="/venues">
          <div class="band__main">
            <h3 class="h3">{{ venue.name }}</h3>
            <p class="meta">
              {{ venue.address }}
              <template v-if="courtCount"> · {{ courtCount }} 片场地</template>
            </p>
          </div>
          <span class="band__go">查看空场</span>
        </RouterLink>
      </section>

      <!-- ===== 球友在聊 ===== -->
      <section class="sec">
        <div class="section-head">
          <h2 class="h2">球友在聊</h2>
          <RouterLink class="more" to="/community">
            去社区
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M9 6l6 6-6 6" />
            </svg>
          </RouterLink>
        </div>

        <div class="panel">
          <template v-if="loading">
            <div v-for="i in 2" :key="i" class="post-skel">
              <AppSkeleton height="32px" width="32px" circle />
              <div class="post-skel__mid">
                <AppSkeleton height="15px" width="50%" />
                <AppSkeleton height="13px" width="70%" style="margin-top: 8px" />
              </div>
            </div>
          </template>

          <RouterLink
            v-for="(p, i) in posts"
            v-else
            :key="p.id"
            class="post"
            :class="{ 'is-first': i === 0 }"
            :to="`/community/${p.id}`"
          >
            <AppAvatar :size="32" :src="p.author_avatar_url" :name="p.author_nickname" />
            <div class="post__main">
              <h3 class="post__title">{{ p.title }}</h3>
              <p class="post__excerpt">{{ excerpt(p.content) }}</p>
              <p class="micro">
                {{ p.author_nickname }} · {{ p.like_count }} 赞 · {{ p.comment_count }} 评论
              </p>
            </div>
          </RouterLink>

          <EmptyState
            v-if="!loading && !posts.length"
            :arc="false"
            title="社区还没有人发言"
            description="聊聊最近打球的心得，或者约人一起打。"
          >
            <slot name="icon" />
            <AppButton variant="primary" to="/community">去发第一条</AppButton>
          </EmptyState>
        </div>
      </section>

      <footer class="foot">
        <span>羽毛球社区</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
/* 首页不吃 .page 的 max-width —— Hero 要更宽的呼吸感，正文再收回来 */
.home {
  padding-bottom: var(--s8);
}

.hero {
  max-width: 1120px;
  margin: 0 auto;
  padding: var(--s9) var(--s5) var(--s6);
}

.hero__title {
  max-width: 15ch;
}

.hero__lead {
  margin-top: var(--s5);
  max-width: 42ch;
  font-size: var(--fs-lead);
  color: var(--ink-2);
}

.hero__cta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s3);
  margin-top: var(--s6);
}

.hero__live {
  margin: var(--s7) 0 0;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.hero__sep {
  margin: 0 6px;
}

.hero__arc {
  display: block;
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 var(--s5);
  opacity: 0.85;
}

.body {
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 var(--s5);
}

.sec {
  margin-top: var(--s8);
}

.more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: var(--fs-sm);
  color: var(--ink-3);
  transition: color 0.14s;
}

.more:hover {
  color: var(--ink);
}

/* ---- 列表行之间的发丝线（首行不加） ---- */
.lined + .lined {
  border-top: 1px solid var(--line);
}

.mini + .mini,
.post + .post {
  border-top: 1px solid var(--line);
}

/* ---- 紧凑行（接下来几天） ---- */
.mini {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  gap: var(--s4);
  align-items: center;
  padding: var(--s3) var(--s5);
  color: inherit;
  transition: background 0.14s;
}

.mini:hover {
  background: var(--surface-2);
}

.mini__when {
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.mini__title {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini__fill {
  display: flex;
  align-items: center;
  gap: var(--s2);
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

/* ---- 场地信息带 ---- */
.band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s5);
  padding: var(--s5);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: inherit;
  transition: border-color 0.14s;
}

.band:hover {
  border-color: var(--ink-3);
}

.band__go {
  flex-shrink: 0;
  padding: 9px 16px;
  border: 1px solid var(--line-2);
  border-radius: var(--r-sm);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink);
}

.band:hover .band__go {
  border-color: var(--ink);
}

/* ---- 社区行 ---- */
.post {
  display: flex;
  gap: var(--s4);
  padding: var(--s4) var(--s5);
  color: inherit;
  transition: background 0.14s;
}

.post:hover {
  background: var(--surface-2);
}

.post__main {
  min-width: 0;
}

.post__title {
  margin: 0 0 2px;
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--ink);
}

.post__excerpt {
  margin: 0 0 4px;
  font-size: var(--fs-sm);
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- 骨架 ---- */
.row-skel {
  display: flex;
  align-items: center;
  gap: var(--s5);
  padding: var(--s4) var(--s5);
}

.row-skel__mid,
.post-skel__mid {
  flex: 1;
}

.row-skel + .row-skel,
.mini-skel + .mini-skel,
.post-skel + .post-skel {
  border-top: 1px solid var(--line);
}

.mini-skel {
  padding: var(--s4) var(--s5);
}

.post-skel {
  display: flex;
  gap: var(--s4);
  align-items: center;
  padding: var(--s4) var(--s5);
}

.foot {
  margin-top: var(--s8);
  padding-top: var(--s5);
  border-top: 1px solid var(--line);
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

/* ---- 移动端 ---- */
@media (max-width: 640px) {
  .hero {
    padding: var(--s7) var(--s4) var(--s5);
  }

  .hero__title {
    max-width: none;
  }

  .hero__cta > * {
    flex: 1;
  }

  .hero__arc,
  .body {
    padding: 0 var(--s4);
  }

  .mini {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: var(--s2) var(--s4);
  }

  .mini__when {
    grid-column: 1 / -1;
  }

  .band {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--s4);
  }
}
</style>
