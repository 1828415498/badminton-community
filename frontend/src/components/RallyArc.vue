<script setup>
/**
 * RallyArc —— 轨迹弧。
 *
 * 这是本项目的视觉母题之一：羽毛球被击出后的抛物线。
 * 它是"属于羽毛球"的形状 —— 比任何渐变都更能让人一眼认出这项运动。
 *
 * 用作 Hero 的分隔、空状态的插画、加载中的装饰。
 * 纯装饰，所以 aria-hidden；不要往里塞信息。
 */
defineProps({
  /** line 发丝灰 / brand 品牌青柠 / ink 墨色 */
  tone: { type: String, default: 'line' },
  /** 垂直翻转，用于区块顶部的分隔 */
  flip: { type: Boolean, default: false },
  /** 只画弧线，不画球头 */
  dashed: { type: Boolean, default: false }
})
</script>

<template>
  <svg
    class="app-arc"
    :class="[`app-arc--${tone}`, { 'is-flip': flip, 'is-dashed': dashed }]"
    viewBox="0 0 640 132"
    fill="none"
    aria-hidden="true"
    focusable="false"
  >
    <path
      d="M0 122 Q 320 -34 640 122"
      stroke="currentColor"
      :stroke-width="dashed ? 1 : 1.5"
      :stroke-dasharray="dashed ? '5 7' : undefined"
      stroke-linecap="round"
    />
    <circle v-if="!dashed" cx="320" cy="44" r="4.5" fill="currentColor" />
  </svg>
</template>

<style scoped>
.app-arc {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}

.app-arc--line {
  color: var(--line-2);
}

.app-arc--brand {
  color: var(--brand);
}

.app-arc--ink {
  color: var(--ink);
}

.app-arc.is-flip {
  transform: scaleY(-1);
}
</style>
