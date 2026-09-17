<script setup>
/**
 * GameRow —— 球局广场的一行（首页专用）。
 *
 * <h2>和 ActivityRow 的区别（为什么不复用）</h2>
 * `ActivityRow` 服务于「球局列表页」：它是<b>纯导航行</b>，整行可点进详情，
 * 行内刻意不放按钮（列表页是"浏览 → 进详情 → 再决定"）。
 * 首页的第一个任务不是浏览而是<b>当场决定</b>：一个真实用户看到
 * 「19:00 新手局 还差 3 人」时已经在决策了，让他再跳一页就是白白丢掉一次转化。
 * 所以这一行必须带「加入」，而"行内带主行动"会反过来改变整行的栅格、
 * 移动端重排和状态机 —— 塞进 ActivityRow 会把列表页也一起改掉。
 * 两个组件服务于两个不同的任务，不是重复。
 *
 * <h2>信息权重（刻意排的）</h2>
 * 时间（最大）> 标题 > 还差几人（品牌色）> 场地 / 已报人数（次要）。
 * 用户扫这一行问的是"这场我能不能去"，所以时间最重、名额第二重，
 * 场地和已报人数是佐证。**同一行只允许一个视觉主角**：整行唯一的
 * 品牌色填充只出现在「加入」按钮上（已加入/已满员/已开始/已结束一律弱化）。
 *
 * <h2>状态机由页面计算，本组件只负责"画"</h2>
 * 页面持有"我报名了哪些活动"和当前时间，才能算出状态；
 * 本组件收到 state 后只决定渲染什么。这样规则只有一处（Home.vue），
 * 不会出现"组件里一套判断、页面里另一套"。
 *
 * state 取值：
 *   join     可加入        → 品牌色「加入」（整行唯一的实心元素）
 *   joined   我已报名      → 「已加入 退出」（弱化，一行文字按钮）
 *   full     已满员        → 灰徽标，不可点
 *   started  已开始        → 灰徽标，报名通道已关（后端会拒绝，所以不能渲染成可点）
 *   over     已结束        → 描边徽标，整行降级
 */
import { computed } from 'vue'
import { AppBadge, AppButton } from '@/components'
import { parseTime, pad } from '@/utils/datetime'

const props = defineProps({
  activity: { type: Object, required: true },
  /** 场地文本（由页面用 court_ids → 场地名映射得到）。空串 = 场地还没加载出来，不显示，见页面里的三段守卫 */
  place: { type: String, default: '' },
  state: { type: String, default: 'join' },
  /** 该行的加入/退出请求进行中 */
  busy: { type: Boolean, default: false }
})

defineEmits(['join', 'quit'])

const start = computed(() => parseTime(props.activity.start_time))

const timeLabel = computed(() =>
  start.value ? `${pad(start.value.h)}:${pad(start.value.m)}` : ''
)

const max = computed(() => props.activity.max_members ?? 0)
const joined = computed(() => props.activity.joined_count ?? 0)
const remaining = computed(() => Math.max(0, max.value - joined.value))

/** 只有"还没开始且没满"的球局才谈得上"还差几人"。 */
const showRemaining = computed(
  () => (props.state === 'join' || props.state === 'joined') && remaining.value > 0
)

/** 只剩 1–2 个位时加重强调：这是"手慢无"的唯一提示手段 */
const urgent = computed(() => remaining.value > 0 && remaining.value <= 2)

const NOTE = { full: '已满员', started: '已开始', over: '已结束' }
const noteKind = { full: 'full', started: 'neutral', over: 'done' }
</script>

<template>
  <div class="game-row" :class="`is-${state}`">
    <span class="game-row__time tnum">{{ timeLabel }}</span>

    <!-- 标题与元信息整块可点进详情：给了足够大的点击区，
         同时把「加入」按钮留在链接外面 —— 按钮嵌在链接里是无效 HTML -->
    <RouterLink class="game-row__main" :to="`/activities/${activity.id}`">
      <h3 class="game-row__title">{{ activity.title }}</h3>
      <p class="game-row__meta">
        <template v-if="place">
          <span class="game-row__place">{{ place }}</span>
          <i class="game-row__dot" aria-hidden="true"></i>
        </template>
        <span class="tnum">已报 {{ joined }}/{{ max }} 人</span>
        <template v-if="showRemaining">
          <i class="game-row__dot" aria-hidden="true"></i>
          <span class="game-row__left tnum" :class="{ 'is-urgent': urgent }">
            还差 {{ remaining }} 人
          </span>
        </template>
      </p>
    </RouterLink>

    <div class="game-row__act">
      <!-- 可加入：整行唯一的实心元素 -->
      <AppButton
        v-if="state === 'join'"
        size="sm"
        :loading="busy"
        @click="$emit('join', activity)"
      >
        加入
      </AppButton>

      <!-- 我已报名：弱化成一行文字按钮，退出是本行唯一的次要动作 -->
      <button
        v-else-if="state === 'joined'"
        type="button"
        class="game-row__quit"
        :disabled="busy"
        @click="$emit('quit', activity)"
      >
        <span class="game-row__quit-state">已加入</span>
        <i class="game-row__dot" aria-hidden="true"></i>
        <span class="game-row__quit-act">退出</span>
      </button>

      <!-- 不可加入：徽标而非按钮。不可操作的元素不用 <button>（不占 Tab 顺序、不会误触） -->
      <AppBadge v-else :kind="noteKind[state]">{{ NOTE[state] }}</AppBadge>
    </div>
  </div>
</template>

<style scoped>
/*
  类名统一 game-row- 前缀。
  原因（踩过的坑）：Vue scoped 会把**父页面的 scope 属性**加到子组件根元素上，
  通用的 `.row` / `.main` 这种类名会被页面样式静默命中，栅格说塌就塌。
*/
.game-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  gap: var(--s4);
  align-items: center;
  padding: var(--s3) var(--s3);
  border-radius: var(--r-sm);
  transition: background 0.14s;
}

.game-row:hover {
  background: var(--surface);
}

/* ---- 时间：这一行最重的信息 ---- */
.game-row__time {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.1;
  color: var(--ink);
}

.game-row.is-over .game-row__time,
.game-row.is-started .game-row__time {
  color: var(--ink-3);
}

/* ---- 标题与元信息 ---- */
.game-row__main {
  min-width: 0;
  display: block;
  color: inherit;
}

.game-row__title {
  margin: 0 0 1px;
  font-size: var(--fs-lead);
  font-weight: 600;
  line-height: 1.3;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.game-row__main:hover .game-row__title {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.game-row.is-over .game-row__title,
.game-row.is-started .game-row__title {
  color: var(--ink-2);
  font-weight: 500;
}

.game-row__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--s2);
  margin: 0;
  font-size: var(--fs-sm);
  line-height: 1.4;
  color: var(--ink-3);
}

/* 分隔符用 CSS 圆点而不是「·」字符：字符在小字号下几乎看不见（1.42:1），
   而且它是排版元素，不该出现在读屏文本里（所以 aria-hidden） */
.game-row__dot {
  flex-shrink: 0;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--line-2);
}

/* "还差 N 人"：品牌色的文字版。荧光色本身不能当文字色（白底 <3:1），
   文字版是 --brand-600（4.9:1）。这是设计规范里明确允许的品牌色文字用法。 */
.game-row__left {
  color: var(--brand-600);
  font-weight: 600;
}

.game-row__left.is-urgent {
  color: var(--ink);
  /* 只剩 1–2 位：加一条荧光下划线，墨色文字保住对比度 */
  box-shadow: inset 0 -0.42em 0 var(--brand);
}

/* ---- 行动列 ---- */
.game-row__act {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-width: 84px;
}

/* 退出：一行文字按钮。刻意不做成按钮块 —— 它不该和「加入」抢注意力 */
.game-row__quit {
  display: inline-flex;
  align-items: center;
  gap: var(--s2);
  padding: var(--s1) var(--s2);
  border: 0;
  border-radius: var(--r-xs);
  background: none;
  font-family: inherit;
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: background 0.14s;
}

.game-row__quit:hover {
  background: var(--surface-2);
}

.game-row__quit:disabled {
  opacity: 0.5;
  cursor: default;
}

.game-row__quit-state {
  color: var(--ink-3);
}

.game-row__quit-act {
  color: var(--ink);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.game-row__quit:hover .game-row__quit-act {
  color: var(--danger);
}

/* ---- 移动端：时间与行动提到第一行，标题下移 ----
   手机上"什么时候 / 能不能加"必须先看到，标题可以晚一行。 */
@media (max-width: 640px) {
  .game-row {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas:
      'time act'
      'main main';
    gap: var(--s2) var(--s3);
    padding: var(--s3) var(--s2);
  }

  .game-row__time {
    grid-area: time;
  }

  .game-row__main {
    grid-area: main;
  }

  .game-row__act {
    grid-area: act;
  }

  .game-row__title {
    white-space: normal;
  }
}
</style>
