<script setup>
/**
 * EmptyState —— 空状态。
 *
 * 取代 Element 默认的那张灰色方块插画（4 个页面在用，是最明显的"模板感"来源）。
 *
 * 原则：空屏必须给出**下一步动作**，不能只是一块白 + 一句"暂无数据"。
 * 插画用轨迹弧 —— 复用品牌母题，比通用插画有识别度，而且体积为零。
 */
import RallyArc from './RallyArc.vue'

defineProps({
  title: { type: String, default: '这里还是空的' },
  description: { type: String, default: '' },
  /** 关掉轨迹弧，改用 icon 插槽 */
  arc: { type: Boolean, default: true }
})
</script>

<template>
  <div class="es">
    <RallyArc v-if="arc" class="es__arc" tone="line" />
    <div v-else class="es__mark"><slot name="icon" /></div>

    <h3 class="es__title">{{ title }}</h3>
    <p v-if="description" class="es__desc">{{ description }}</p>

    <div v-if="$slots.default" class="es__actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.es {
  padding: var(--s6) var(--s5) var(--s7);
  text-align: center;
}

.es__arc {
  max-width: 320px;
  margin: 0 auto var(--s5);
  opacity: 0.9;
}

.es__mark {
  width: 44px;
  height: 44px;
  margin: 0 auto var(--s4);
  border-radius: var(--r-sm);
  background: var(--surface-2);
  color: var(--ink-3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.es__title {
  margin: 0 0 var(--s1);
  font-size: var(--fs-h3);
  font-weight: 600;
  color: var(--ink);
}

.es__desc {
  margin: 0 auto;
  max-width: 360px;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.es__actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--s3);
  margin-top: var(--s5);
}
</style>
