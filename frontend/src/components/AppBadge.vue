<script setup>
/**
 * AppBadge —— 状态徽标。
 *
 * 两个刻意的决定：
 * 1. **接近直角（2px 圆角）**。全站都是圆角胶囊会显得"软"且没有层级，
 *    徽标这种小元素用近乎直角反而更利落，也和"场线"的直线母题一致。
 * 2. **颜色之外还有一个圆点**。状态不能只靠颜色传达（色觉障碍用户读不到），
 *    所以每个徽标左边都有一个形状标记做第二重编码。
 *
 * 状态语义由页面映射（后端 status 是 0/1/2/3/4，不该泄漏进组件）。
 */
defineProps({
  /** open 可加入 / full 已满 / pending 待审核 / done 已结束 / cancelled 已取消 / brand 强调 / neutral 中性 */
  kind: { type: String, default: 'neutral' },
  /** 关掉左侧圆点（用在已经有图标的地方） */
  plain: { type: Boolean, default: false }
})

const DEFAULT_LABEL = {
  open: '可加入',
  full: '已满',
  pending: '待审核',
  done: '已结束',
  cancelled: '已取消',
  brand: '',
  neutral: ''
}
</script>

<template>
  <span class="app-badge" :class="`app-badge--${kind}`">
    <i v-if="!plain" class="app-badge__dot" aria-hidden="true"></i>
    <slot>{{ DEFAULT_LABEL[kind] }}</slot>
  </span>
</template>

<style scoped>
.app-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 7px;
  border: 1px solid transparent;
  border-radius: var(--r-xs);
  font-size: var(--fs-xs);
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.app-badge__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.app-badge--open {
  background: var(--ok-100);
  color: var(--ok);
}

.app-badge--full {
  background: var(--surface-2);
  color: var(--ink-3);
}

.app-badge--pending {
  background: var(--warn-100);
  color: var(--warn);
}

.app-badge--done {
  background: transparent;
  border-color: var(--line-2);
  color: var(--ink-3);
}

.app-badge--cancelled {
  background: transparent;
  border-color: var(--line);
  color: var(--ink-3);
}

/* 品牌色徽标：荧光底 + 墨字（不能反过来） */
.app-badge--brand {
  background: var(--brand);
  color: var(--brand-ink);
}

.app-badge--neutral {
  background: var(--surface-2);
  color: var(--ink-2);
}
</style>
