<script setup>
/**
 * AppButton —— 全站唯一的按钮组件。
 *
 * 为什么不用 el-button：
 * 1. Element 的 primary 是「primary 填充 + 白字」，而我们的品牌色是荧光青柠，
 *    配白字对比度只有 1.3:1，完全不可读。自己写才能用「品牌填充 + 墨色文字」。
 * 2. Element 一个按钮组件有 30+ 个视觉分支，我们只需要 4 个变体。
 *
 * 变体约定（重要）：
 *   primary  —— 品牌色填充。**一屏内只允许出现一个。**
 *   outline  —— 次要动作（取消、返回）
 *   ghost    —— 第三级、放在信息行末尾的动作
 *   danger   —— 破坏性动作（取消预约、删除）
 */
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  variant: { type: String, default: 'primary' },
  size: { type: String, default: 'md' },
  nativeType: { type: String, default: 'button' },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  block: { type: Boolean, default: false },
  /** 传了就渲染成 router-link（导航型按钮不该是 <button>） */
  to: { type: [String, Object], default: null }
})

const tag = computed(() => (props.to ? RouterLink : 'button'))
const inert = computed(() => props.disabled || props.loading)

const bindings = computed(() =>
  props.to
    ? { to: props.to, tabindex: inert.value ? -1 : undefined }
    : { type: props.nativeType, disabled: inert.value }
)
</script>

<template>
  <component
    :is="tag"
    v-bind="{ ...$attrs, ...bindings }"
    class="app-btn"
    :class="[
      `app-btn--${variant}`,
      `app-btn--${size}`,
      { 'app-btn--block': block, 'is-inert': inert, 'is-loading': loading }
    ]"
  >
    <span v-if="loading" class="app-btn__spin" aria-hidden="true"></span>
    <slot />
  </component>
</template>

<style scoped>
.app-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--s2);
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  font-family: inherit;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.14s, border-color 0.14s, color 0.14s;
}

/* ---- 尺寸：三档，触控高度不低于 32px ---- */
.app-btn--sm {
  height: 32px;
  padding: 0 var(--s3);
  font-size: var(--fs-sm);
}

.app-btn--md {
  height: 42px;
  padding: 0 var(--s4);
  font-size: var(--fs-body);
}

.app-btn--lg {
  height: 52px;
  padding: 0 var(--s6);
  font-size: 16px;
}

.app-btn--block {
  width: 100%;
}

/* ---- 变体 ---- */

/* 品牌色填充 + 墨色文字：对比度 14.6:1。
   悬停/按下用略深的青柠，不用透明度 —— 荧光色降透明度会变浑。 */
.app-btn--primary {
  background: var(--brand);
  border-color: var(--brand);
  color: var(--brand-ink);
}

.app-btn--primary:hover {
  background: #b9e82a;
  border-color: #b9e82a;
}

.app-btn--primary:active {
  background: #abda1d;
  border-color: #abda1d;
}

.app-btn--outline {
  background: transparent;
  border-color: var(--line-2);
  color: var(--ink);
}

.app-btn--outline:hover {
  border-color: var(--ink);
}

.app-btn--ghost {
  background: transparent;
  color: var(--ink-2);
}

.app-btn--ghost:hover {
  background: var(--surface-2);
  color: var(--ink);
}

.app-btn--danger {
  background: transparent;
  color: var(--danger);
}

.app-btn--danger:hover {
  background: var(--danger-100);
}

/* ---- 状态 ---- */
.is-inert {
  opacity: 0.45;
  cursor: not-allowed;
  pointer-events: none;
}

.app-btn__spin {
  width: 13px;
  height: 13px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: app-btn-spin 0.6s linear infinite;
}

@keyframes app-btn-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
