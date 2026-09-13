<script setup>
/**
 * AppSkeleton —— 骨架占位。
 *
 * 用"呼吸"（透明度脉动）而不是"扫光"（横向高光扫过）。
 * 扫光是 AI 生成界面的高频特征，而且更闪。
 */
defineProps({
  width: { type: String, default: '100%' },
  height: { type: String, default: '14px' },
  radius: { type: String, default: 'var(--r-xs)' },
  circle: { type: Boolean, default: false }
})
</script>

<template>
  <span
    class="app-sk"
    :style="{
      width: circle ? height : width,
      height,
      borderRadius: circle ? '50%' : radius
    }"
    aria-hidden="true"
  ></span>
</template>

<style scoped>
.app-sk {
  display: block;
  /* 用 --line 而不是 --surface-2：实测 --surface-2 落在页面底色上几乎看不见 */
  background: var(--line);
  animation: sk-pulse 1.5s ease-in-out infinite;
}

@keyframes sk-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>
