<script setup>
/**
 * ActivityDetail —— 球局详情。
 *
 * <h2>改造前的问题</h2>
 * 整页是 `el-descriptions border`（数据库行详情控件）：
 * 时间直接给用户看 `2026-09-14T17:00:00 ~ 19:00`、场地显示成 `{{cid}} 号场`、
 * 所有字段同样粗细（时间与"审核时间"视觉权重一样）。
 * 而第一个按钮是「返回列表」（系统导航），真正要做的「报名参加」被压在
 * 描述字段下面 —— 用户得滚过一整块数据表才找到 CTA。
 * 另有 3 条 `el-alert` 把后端状态码讲给用户听。
 *
 * <h2>改造后的结构</h2>
 * 这一页只需要回答一个问题：**我要不要加入这场球。**
 * 所以首屏（右侧操作栏 + 左侧时间块）直接给出决定所需的四件事：
 * 什么时候 → 在哪 → 都有谁 → 还差几人，CTA 在同一屏内。
 *
 * <h2>三个决定</h2>
 * <ol>
 *   <li><b>CTA 做成固定操作栏。</b>桌面端是右侧粘性栏，移动端变成底部固定条
 *       —— 同一条 DOM，靠 CSS 切换定位。用户滚到哪都能加入。</li>
 *   <li><b>「返回球局」降级成一行轻链接</b>，不再是按钮。返回是导航，不是动作；
 *       把它做成按钮会跟真正的 CTA 抢注意力。</li>
 *   <li><b>场地 ID 映射成真实场号</b>。`court_ids` 存的是 court 表的 id，
 *       旧代码直接渲染成「{id} 号场」——在当前种子数据里侥幸对了（id 恰好等于场号），
 *       但只要场地改名或多出一个场地就会显示错的场地。这里用场馆接口换真实名称，
 *       顺带补上"在哪打球"（场馆名）——这是原来整页都缺失的信息。</li>
 * </ol>
 */
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
import { listVenues, listCourts } from '@/api/venue'
import { useUserStore } from '@/stores/user'
import {
  AppButton,
  AppBadge,
  AppAvatar,
  EmptyState,
  AppSkeleton
} from '@/components'
import { parseTime, dateLong, timeRange, pad } from '@/utils/datetime'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const id = Number(route.params.id)
const loading = ref(true)
const activity = ref(null)
const members = ref([])
const acting = ref(false)

/** court_id → 场地名，以及场馆名。数据来自场馆接口，用于把 ID 显示成人能读的场地 */
const courtNames = ref({})
const venueName = ref('')

/**
 * 状态语义映射。后端 status 是 0/1/2/3/4，不该泄漏给用户 ——
 * 用户看到的是「报名中 / 待审核 / 已驳回」，不是数字。
 */
const STATUS = {
  0: { kind: 'pending', text: '待审核' },
  1: { kind: 'open', text: '报名中' },
  2: { kind: 'rejected', text: '已驳回' },
  3: { kind: 'cancelled', text: '已取消' },
  4: { kind: 'done', text: '已结束' }
}

const statusInfo = computed(
  () => STATUS[activity.value?.status] ?? { kind: 'neutral', text: '' }
)

const isCreator = computed(
  () => !!activity.value && !!store.user && activity.value.creator_id === store.user.id
)

const isAdmin = computed(() => store.user?.role === 1)

const joined = computed(() => members.value.some((m) => m.user_id === store.user?.id))

const remaining = computed(() => {
  if (!activity.value) return 0
  return Math.max(0, activity.value.max_members - activity.value.joined_count)
})

const isFull = computed(() => !!activity.value && remaining.value === 0)

/** 名单可见性：与后端 RegistrationService.listMembers 的规则保持一致 */
const canSeeMembers = computed(
  () => !!activity.value && ([1, 4].includes(activity.value.status) || isCreator.value || isAdmin.value)
)

/**
 * 报名窗口是否还开着：活动已发布，且尚未开始。
 * 后端同样拒绝已开始的球局，这里对齐，避免渲染出"点了必然报错"的按钮。
 */
const windowOpen = computed(() => {
  if (!activity.value || activity.value.status !== 1) return false
  const s = parseTime(activity.value.start_time)
  return !!s && s.date.getTime() > Date.now()
})

/**
 * 加入与退出是**两个独立条件**，不能塞进同一个 canJoin。
 * （踩过的坑：把 `!joined` 并进 canJoin 之后，已加入的人算不出"可退出"，
 * 发起人那一侧只剩一行文字，退出按钮直接消失 —— 是看截图才发现的。）
 */
const canJoin = computed(() => windowOpen.value && !joined.value && !isFull.value)
const canQuit = computed(() => windowOpen.value && joined.value)

/**
 * 操作栏状态机。
 *
 * 用单一状态而不是在模板里堆 v-if/v-else-if 链 —— 5 个后端状态 ×
 * 是否已报名 × 是否已满 × 是否已开始，条件组合很容易漏掉一格，
 * 漏掉的那格就会渲染出"点了报错"的按钮、或者干脆什么都不渲染。
 * 这里把每种组合显式列出。
 */
const cta = computed(() => {
  const a = activity.value
  if (!a) return { kind: 'none' }
  if (a.status === 0) return { kind: 'note', text: '审核通过后开放报名' }
  if (a.status === 2) return { kind: 'note', text: '这场球局没有通过审核' }
  if (a.status === 3) return { kind: 'note', text: '发起人已取消这场球局' }
  if (a.status === 4) return { kind: 'note', text: '这场球已经打完了' }

  // 以下都是 status === 1（报名中）
  if (canJoin.value) return { kind: 'join' }
  if (canQuit.value) return { kind: 'quit' }
  if (joined.value) return { kind: 'joined-only' }
  if (isFull.value) return { kind: 'full' }
  return { kind: 'note', text: '报名已截止' }
})

// ---------- 时间展示 ----------
const startP = computed(() => parseTime(activity.value?.start_time))
const endP = computed(() => parseTime(activity.value?.end_time))

const dayLabel = computed(() => (activity.value ? dateLong(activity.value.start_time) : ''))
const clockLabel = computed(() =>
  activity.value ? timeRange(activity.value.start_time, activity.value.end_time) : ''
)

const durationLabel = computed(() => {
  if (!startP.value || !endP.value) return ''
  const h = (endP.value.date - startP.value.date) / 3600000
  if (h <= 0) return ''
  return Number.isInteger(h) ? `${h} 小时` : `${h.toFixed(1)} 小时`
})

/** 开场还有多久 —— 「还剩 3 小时开始」比一串时间戳有用得多 */
const startsIn = computed(() => {
  if (!startP.value) return ''
  const mins = Math.round((startP.value.date.getTime() - Date.now()) / 60000)
  if (mins <= 0) return ''
  if (mins < 60) return `${mins} 分钟后开始`
  const hours = Math.round(mins / 60)
  if (hours < 24) return `${hours} 小时后开始`
  return ''
})

/**
 * 名额区块该说什么。
 *
 * 「还差 N 人」只有在**真的还能加入**时才成立。已结束 / 已取消 / 待审核的球局
 * 显示"还差 6 人"是在暗示用户可以补位，而实际上没有名额可抢 —— 那是在传递假信息。
 * 同样地，已取消 / 已驳回的球局里"已报 X 人"也没意义（报名已全部作废）。
 */
const capacity = computed(() => {
  const a = activity.value
  if (!a) return { mode: 'none' }
  const open = a.status === 1
  if (open) {
    return remaining.value > 0
      ? { mode: 'hurry', showCount: true }
      : { mode: 'full', showCount: true }
  }
  const label =
    a.status === 0
      ? '还没开放报名'
      : a.status === 4
        ? '这场球已经打完'
        : a.status === 3
          ? '球局已取消'
          : '未开放报名'
  return { mode: 'closed', label, showCount: [0, 4].includes(a.status) }
})

// ---------- 场地 ----------
const courtsText = computed(() => {
  const ids = activity.value?.court_ids || []
  if (!ids.length) return ''
  return ids.map((cid) => courtNames.value[cid] || `${cid} 号场`).join(' · ')
})

const placeText = computed(() => {
  if (venueName.value && courtsText.value) return `${venueName.value} · ${courtsText.value}`
  return venueName.value || courtsText.value
})

// ---------- 数据 ----------
async function load() {
  loading.value = true
  try {
    activity.value = await getActivity(id)
    // 名单：后端对 0/2/3 状态只放行发起人与管理员，所以这里也要判断
    if (canSeeMembers.value) {
      try {
        const res = await listMembers(id, { page: 1, page_size: 50 })
        members.value = res.list || []
      } catch (e) {
        members.value = []
      }
    } else {
      members.value = []
    }
  } catch (e) {
    // 后端对非公开球局（待审核/已驳回/已取消）只放行发起人与管理员，
    // 其他人拿到的是「活动不存在或无权查看」。
    // 这里刻意接住：activity 保持 null，页面渲染「这场球局不存在」的空状态，
    // 而不是让拒绝逃逸成未处理的 Promise 拒绝。
    activity.value = null
    members.value = []
  } finally {
    loading.value = false
  }
}

/** 场地名只依赖场馆数据，与球局无关；失败了不影响主内容 */
async function loadPlaces() {
  try {
    const venues = await listVenues()
    if (!venues?.length) return
    venueName.value = venues[0].name || ''
    const courts = await listCourts(venues[0].id, { status: 1 })
    const map = {}
    for (const c of courts || []) map[c.id] = c.name
    courtNames.value = map
  } catch (e) {
    /* 拿不到就退回「N 号场」 */
  }
}

// ---------- 动作 ----------
async function doJoin() {
  acting.value = true
  try {
    await registerActivity(id)
    ElMessage.success('已加入，记得准时到场')
    await load()
  } finally {
    acting.value = false
  }
}

async function doQuit() {
  await ElMessageBox.confirm('确认退出这场球吗？名额会释放给其他球友。', '退出球局', {
    type: 'warning',
    confirmButtonText: '退出'
  })
  acting.value = true
  try {
    await cancelRegistration(id)
    ElMessage.success('已退出')
    await load()
  } finally {
    acting.value = false
  }
}

async function doCancelActivity() {
  await ElMessageBox.confirm(
    '确认取消这场球局吗？场地会立即释放，所有报名作废。',
    '取消球局',
    { type: 'warning', confirmButtonText: '确认取消' }
  )
  acting.value = true
  try {
    await cancelActivity(id)
    ElMessage.success('球局已取消')
    await load()
  } finally {
    acting.value = false
  }
}

onMounted(() => {
  load()
  loadPlaces()
})
</script>

<template>
  <div class="detail">
    <!-- 骨架：形状与真实内容一致，避免"先空后跳" -->
    <div v-if="loading" class="page">
      <AppSkeleton height="14px" width="72px" />
      <AppSkeleton height="34px" width="58%" style="margin-top: 20px" />
      <div class="grid" style="margin-top: 28px">
        <div class="panel panel-pad">
          <AppSkeleton height="52px" width="60%" />
          <AppSkeleton height="18px" width="40%" style="margin-top: 14px" />
          <AppSkeleton height="70px" style="margin-top: 24px" />
        </div>
        <div class="panel panel-pad">
          <AppSkeleton height="44px" />
          <AppSkeleton height="16px" width="70%" style="margin-top: 14px" />
        </div>
      </div>
    </div>

    <div v-else-if="!activity" class="page">
      <EmptyState
        title="这场球局不存在"
        description="可能已被发起人取消，或者链接不对。"
      >
        <AppButton to="/activities">回到球局列表</AppButton>
      </EmptyState>
    </div>

    <div v-else class="page">
      <!-- 返回：一行轻链接。返回是导航不是动作，做成按钮会跟 CTA 抢注意力 -->
      <RouterLink class="back" to="/activities">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M15 6l-6 6 6 6" />
        </svg>
        球局
      </RouterLink>

      <header class="hd">
        <AppBadge :kind="statusInfo.kind">{{ statusInfo.text }}</AppBadge>
        <h1 class="hd__title">{{ activity.title }}</h1>
        <p class="hd__by">
          {{ activity.creator_nickname }} 发起
          <template v-if="startsIn"> · {{ startsIn }}</template>
        </p>
      </header>

      <div class="grid">
        <!-- ============ 主区 ============ -->
        <div class="col">
          <!-- 时间：这一页信息量最大的一块 -->
          <section class="panel panel-pad when">
            <p class="when__day">{{ dayLabel }}</p>
            <p class="when__clock tnum">{{ clockLabel }}</p>
            <p class="when__meta">
              <template v-if="durationLabel">{{ durationLabel }}</template>
              <template v-if="durationLabel && placeText"> · </template>
              <template v-if="placeText">{{ placeText }}</template>
            </p>
          </section>

          <!-- 阵容：名额 + 名单合成一块。
               拆成两块会让同一个球友的名字在页面上出现两次（名额区的头像堆叠、
               名单区的一行），而它们说的本来就是同一件事。 -->
          <section class="panel roster">
            <div class="roster__count">
              <div v-if="capacity.mode === 'hurry'" class="slots__num">
                <span class="slots__pre">还差</span>
                <span class="slots__big tnum">{{ remaining }}</span>
                <span class="slots__unit">人</span>
              </div>
              <p v-else-if="capacity.mode === 'full'" class="slots__full">这场已经满了</p>
              <p v-else class="slots__closed">{{ capacity.label }}</p>

              <p v-if="capacity.showCount" class="slots__meta tnum">
                已报 {{ activity.joined_count }} / {{ activity.max_members }}
              </p>
            </div>

            <div class="panel-head">
              <h2 class="panel-title">都有谁</h2>
              <span v-if="members.length" class="meta tnum">{{ members.length }} 人</span>
            </div>

            <ul v-if="members.length" class="members__list">
              <li v-for="m in members" :key="m.id" class="member">
                <AppAvatar :size="34" :src="m.avatar_url" :name="m.nickname" />
                <span class="member__name">{{ m.nickname }}</span>
                <span v-if="m.user_id === activity.creator_id" class="member__tag">发起人</span>
              </li>
            </ul>

            <p v-else-if="!canSeeMembers" class="members__hint">
              报名名单仅发起人与管理员可见。
            </p>
            <p v-else class="members__hint">还没有人报名，你可以做第一个。</p>
          </section>

          <!-- 状态说明：只有异常状态才出现。用描边 + 图标，不用整块色底
               —— 每页都有的色块提示条会变成噪音。 -->
          <section
            v-if="[0, 2, 3].includes(activity.status)"
            class="panel panel-pad notice"
            :class="`notice--${activity.status}`"
          >
            <svg class="notice__icon" width="17" height="17" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round"
                 stroke-linejoin="round" aria-hidden="true">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 8v5M12 16h.01" />
            </svg>
            <p class="notice__text">
              <template v-if="activity.status === 0">
                等管理员审核通过后才会开放报名。场地已经锁定，别人抢不走。
              </template>
              <template v-else-if="activity.status === 2">
                管理员驳回了这场球局<template v-if="activity.reject_reason">，原因：{{ activity.reject_reason }}</template>。场地已释放。
              </template>
              <template v-else>发起人已取消这场球局，场地已释放。</template>
            </p>
          </section>

          <!-- 描述 -->
          <section v-if="activity.description" class="panel panel-pad desc">
            <h2 class="panel-title desc__h">发起人的话</h2>
            <p class="desc__body">{{ activity.description }}</p>
          </section>
        </div>

        <!-- ============ 操作栏 ============
             桌面端：右侧粘性栏，滚动时始终可见
             移动端：变成屏幕底部固定条（同一条 DOM，只切定位） -->
        <aside class="side">
          <div class="panel panel-pad side__inner">
            <p v-if="cta.kind === 'join'" class="side__lead">
              加入后可以随时退出
            </p>
            <p v-else-if="cta.kind === 'full'" class="side__lead">名额已满</p>
            <p v-else-if="cta.kind === 'quit' || cta.kind === 'joined-only'" class="side__lead">
              你已在这场球局里
            </p>
            <p v-else class="side__lead">{{ cta.text }}</p>

            <div class="side__cta">
              <AppButton
                v-if="cta.kind === 'join'"
                size="lg"
                block
                :loading="acting"
                @click="doJoin"
              >
                加入球局
              </AppButton>

              <AppButton
                v-else-if="cta.kind === 'quit'"
                size="lg"
                block
                variant="outline"
                :loading="acting"
                @click="doQuit"
              >
                退出球局
              </AppButton>

              <AppButton v-else-if="cta.kind === 'full'" size="lg" block disabled>
                名额已满
              </AppButton>
            </div>

            <!-- 发起人的次要动作：明确降级，不和主 CTA 并排 -->
            <button
              v-if="isCreator && [0, 1].includes(activity.status)"
              type="button"
              class="side__danger"
              :disabled="acting"
              @click="doCancelActivity"
            >
              取消这场球局
            </button>
          </div>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 移动端要给底部固定操作栏留出空间 */
.detail {
  padding-bottom: 0;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: var(--fs-sm);
  color: var(--ink-3);
  text-decoration: none;
  transition: color 0.14s;
}

.back:hover {
  color: var(--ink);
}

.hd {
  margin: var(--s3) 0 var(--s6);
}

.hd__title {
  margin: var(--s3) 0 var(--s2);
  font-family: var(--font-display);
  font-size: var(--fs-h1);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.12;
  color: var(--ink);
}

.hd__by {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 292px;
  gap: var(--s6);
  align-items: start;
}

.col {
  display: flex;
  flex-direction: column;
  gap: var(--s4);
  min-width: 0;
}

/* ---- 时间块 ---- */
.when__day {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.when__clock {
  margin: 4px 0 0;
  font-family: var(--font-display);
  font-size: var(--fs-stat);
  font-weight: 700;
  letter-spacing: -0.025em;
  line-height: 1.05;
  color: var(--ink);
}

.when__meta {
  margin: var(--s2) 0 0;
  font-size: var(--fs-body);
  color: var(--ink-2);
}

/* ---- 阵容（名额 + 名单）---- */
.roster__count {
  padding: var(--s5);
}

/* 名单表头紧跟名额区时补一条上边线（.panel-head 只带下边线） */
.roster__count + .panel-head {
  border-top: 1px solid var(--line);
}

.slots__num {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.slots__pre {
  font-size: var(--fs-body);
  color: var(--ink-3);
}

.slots__big {
  font-family: var(--font-display);
  font-size: var(--fs-stat);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1;
  color: var(--ink);
  /* 荧光笔划线：数字保持墨色保住对比度，同时拿到品牌识别 */
  box-shadow: inset 0 -0.32em 0 var(--brand);
}

.slots__unit {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--ink-3);
}

.slots__full {
  font-size: var(--fs-h3);
  font-weight: 600;
  color: var(--ink-3);
}

/* 不能报名时的说明：安静一行，不占大字号（大字号必须留给真能行动的情况） */
.slots__closed {
  margin: 0;
  font-size: var(--fs-body);
  color: var(--ink-2);
}

.slots__meta {
  margin: var(--s2) 0 0;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

/* ---- 状态说明：描边 + 图标，不用整块色底 ---- */
.notice {
  display: flex;
  align-items: flex-start;
  gap: var(--s3);
  border-color: var(--line-2);
}

.notice__icon {
  flex-shrink: 0;
  margin-top: 1px;
  color: var(--warn);
}

.notice--2 .notice__icon {
  color: var(--danger);
}

.notice--3 .notice__icon {
  color: var(--ink-3);
}

.notice__text {
  margin: 0;
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--ink-2);
}

/* ---- 描述 ---- */
.desc__h {
  margin: 0 0 var(--s2);
}

.desc__body {
  margin: 0;
  font-size: var(--fs-body);
  line-height: 1.7;
  color: var(--ink-2);
  white-space: pre-wrap;
}

/* ---- 名单 ---- */
.members__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.member {
  display: flex;
  align-items: center;
  gap: var(--s3);
  padding: var(--s3) var(--s5);
}

.member + .member {
  border-top: 1px solid var(--line);
}

.member__name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-body);
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member__tag {
  font-size: var(--fs-xs);
  color: var(--ink-3);
  border: 1px solid var(--line);
  border-radius: var(--r-xs);
  padding: 1px 6px;
}

.members__hint {
  margin: 0;
  padding: var(--s5);
  font-size: var(--fs-sm);
  color: var(--ink-3);
  border-top: 1px solid var(--line);
}

/* ---- 操作栏 ---- */
.side__lead {
  margin: 0 0 var(--s3);
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.side__lead b {
  font-family: var(--font-display);
  font-size: var(--fs-lead);
  color: var(--ink);
}

.side__cta {
  display: flex;
  flex-direction: column;
  gap: var(--s2);
}

/* 危险动作：明确降级成一行文字按钮，不与主 CTA 争视觉权重 */
.side__danger {
  margin-top: var(--s4);
  padding: 0;
  border: 0;
  background: none;
  font-family: inherit;
  font-size: var(--fs-sm);
  color: var(--ink-3);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.side__danger:hover {
  color: var(--danger);
}

.side__danger:disabled {
  opacity: 0.5;
  cursor: default;
}

/* 桌面端：操作栏粘在视口内，滚到哪都能加入 */
@media (min-width: 901px) {
  .side {
    position: sticky;
    top: var(--s4);
  }
}

/* 窄屏：操作栏变成底部固定条。移动端拇指够不到页面中部，
   把 CTA 钉在底部比"跟着内容滚"可靠得多。 */
@media (max-width: 900px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .side {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 30;
    padding: 0 var(--s4);
    padding-bottom: calc(var(--s3) + env(safe-area-inset-bottom, 0px));
  }

  /* 底部条只留 CTA，把解释性文字收掉 —— 空间要留给按钮 */
  .side__inner {
    border-radius: var(--r-md) !important;
    box-shadow: 0 -6px 24px rgba(16, 19, 17, 0.1);
    padding: var(--s3);
  }

  .side__lead,
  .side__danger {
    display: none;
  }

  /* 给固定条让出高度，否则最后一块内容会被挡住 */
  .detail {
    padding-bottom: 108px;
  }
}
</style>
