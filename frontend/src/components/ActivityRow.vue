<script setup>
/**
 * ActivityRow —— 球局行。首页与球局列表共用。
 *
 * 信息权重是刻意排的：**时间 > 剩余名额 > 标题 > 发起人**。
 * 理由：用户扫这一行时真正在问的是"这场球我能不能去" ——
 * 所以左边是时间（决定去不去），右边是「还差 N 人」（决定抢不抢得到），
 * 标题反而排在两者之后。原来那种"所有字段一样大"的卡片回答不了这个问题。
 *
 * 另一个决定：**行内不放「加入」按钮**，整行可点进详情。
 * 一屏六行六个按钮会让页面按钮泛滥；而且"都有谁去"只有在详情页才看得到，
 * 那才是用户下决心的地方。加入动作归详情页所有。
 */
import { computed } from 'vue'
import AppBadge from './AppBadge.vue'
import StatNumber from './StatNumber.vue'
import { parseTime, dayName, pad } from '@/utils/datetime'

const props = defineProps({
  activity: { type: Object, required: true },
  /**
   * 'open' | 'past'
   *
   * past 时右侧不再显示"还差 N 人" —— 已结束的球局没有名额可抢，
   * 显示人数只是把数据库字段摆出来。改为一个明确的「已结束」。
   */
  state: { type: String, default: 'open' }
})

const start = computed(() => parseTime(props.activity.start_time))
const end = computed(() => parseTime(props.activity.end_time))

const dayLabel = computed(() => (start.value ? dayName(start.value.date) : ''))

const timeLabel = computed(() =>
  start.value ? `${pad(start.value.h)}:${pad(start.value.m)}` : ''
)

const spanLabel = computed(() => {
  if (!start.value || !end.value) return ''
  const hours = (end.value.date - start.value.date) / 3600000
  if (hours <= 0) return ''
  return Number.isInteger(hours) ? `${hours} 小时` : `${hours.toFixed(1)} 小时`
})

/** 完整时间段，用于悬浮提示与读屏 */
const fullRange = computed(() => {
  if (!start.value || !end.value) return ''
  const d = `${start.value.date.getMonth() + 1} 月 ${start.value.date.getDate()} 日`
  return `${d} ${pad(start.value.h)}:${pad(start.value.m)}–${pad(end.value.h)}:${pad(end.value.m)}`
})

const remaining = computed(() =>
  Math.max(0, (props.activity.max_members ?? 0) - (props.activity.joined_count ?? 0))
)
const isFull = computed(() => remaining.value === 0)
/** 只剩 1–2 个位置时用品牌色强调：这是"手慢无"的唯一提示手段 */
const urgent = computed(() => remaining.value > 0 && remaining.value <= 2)
</script>

<template>
  <RouterLink
    class="act-row"
    :class="{ 'is-past': state === 'past' }"
    :to="`/activities/${activity.id}`"
    :aria-label="`${dayLabel} ${timeLabel} ${activity.title}，${isFull ? '已满' : `还差 ${remaining} 人`}`"
  >
    <div class="act-row__when">
      <span class="act-row__day">{{ dayLabel }}</span>
      <span class="act-row__time tnum">{{ timeLabel }}</span>
      <span class="act-row__span tnum" :title="fullRange">{{ spanLabel }}</span>
    </div>

    <div class="act-row__main">
      <h3 class="act-row__title">{{ activity.title }}</h3>
      <p class="act-row__meta">
        {{ activity.creator_nickname }} 发起
        <template v-if="!isFull"> · 已报 {{ activity.joined_count }} 人</template>
      </p>
    </div>

    <div class="act-row__slots">
      <span v-if="state === 'past'" class="act-row__over">已结束</span>
      <AppBadge v-else-if="isFull" kind="full" />
      <StatNumber
        v-else
        :value="remaining"
        unit="人"
        :label="urgent ? '就缺' : '还差'"
        label-position="before"
        size="md"
        :tone="urgent ? 'brand' : 'ink'"
      />
    </div>
  </RouterLink>
</template>

<style scoped>
/*
  类名统一加 act- 前缀。
  原因（踩过的坑）：Vue 的 scoped 样式会把**父组件的 scope 属性**加到子组件的根元素上，
  所以父页面里一条 `.row { display: flex }` 会直接命中本组件的根元素，
  把这里的 grid 布局打掉。组件根类名必须带命名空间，否则随时会被页面样式踩。
*/
.act-row {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) auto;
  gap: var(--s5);
  align-items: center;
  padding: var(--s4) var(--s5);
  color: inherit;
  transition: background 0.14s;
}

.act-row:hover {
  background: var(--surface-2);
}

/* 已结束：整行降级，但仍然可点进详情（历史球局同样有参考价值） */
.act-row.is-past .act-row__time,
.act-row.is-past .act-row__title {
  color: var(--ink-3);
}

.act-row.is-past .act-row__time {
  font-weight: 600;
}

.act-row__over {
  font-size: var(--fs-sm);
  color: var(--ink-3);
  white-space: nowrap;
}

/*
  相邻两行之间一条发丝线。
  用 `+` 而不是给每行加 border-bottom —— 这样把组件单独用在一处时不会多出一条悬空的线。
  分隔靠描边而非投影，与全站一致。
*/
.act-row + .act-row {
  border-top: 1px solid var(--line);
}

/* ---- 时间块：这一行最重的信息 ---- */
.act-row__when {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.act-row__day {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.act-row__time {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.1;
  color: var(--ink);
}

.act-row__span {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

/* ---- 标题与元信息 ---- */
.act-row__main {
  min-width: 0;
}

.act-row__title {
  margin: 0 0 2px;
  font-size: var(--fs-lead);
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.act-row__meta {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.act-row__slots {
  min-width: 84px;
  text-align: right;
}

/* ---- 移动端：时间与名额提到第一行，标题下移 ---- */
@media (max-width: 640px) {
  .act-row {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas:
      'when slots'
      'main main';
    gap: var(--s2) var(--s4);
    padding: var(--s4);
  }

  .act-row__when {
    grid-area: when;
    flex-direction: row;
    align-items: baseline;
    gap: var(--s2);
  }

  .act-row__main {
    grid-area: main;
  }

  .act-row__slots {
    grid-area: slots;
  }

  .act-row__title {
    white-space: normal;
  }
}
</style>
