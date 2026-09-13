<script setup>
/**
 * AppSheet —— 弹层。取代 el-dialog。
 *
 * 为什么不直接用 el-dialog：
 * Element 的对话框自带标题栏样式、关闭按钮位置、遮罩色，全部要覆盖；
 * 而且它的动画是"从中心缩放"，偏重。我们要的是一句话能讲清的动效。
 *
 * 形态随屏幕变：
 *   桌面 —— 居中卡片
 *   移动 —— 从底部升起的面板（拇指够得到，这是移动端的正确形态）
 *
 * 无障碍：Esc 关闭、遮罩点击关闭、打开时聚焦面板、Tab 焦点锁在面板内、
 * 关闭后焦点回到触发元素、打开期间锁 body 滚动。
 */
import { watch, nextTick, ref, onBeforeUnmount } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: String, default: '520px' }
})

const emit = defineEmits(['update:modelValue'])

const panel = ref(null)
let lastFocused = null

function close() {
  emit('update:modelValue', false)
}

function onKeydown(e) {
  if (e.key === 'Escape') {
    close()
    return
  }
  if (e.key !== 'Tab' || !panel.value) return

  const nodes = panel.value.querySelectorAll(
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  )
  if (!nodes.length) return

  const first = nodes[0]
  const last = nodes[nodes.length - 1]

  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault()
    last.focus()
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault()
    first.focus()
  }
}

watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      lastFocused = document.activeElement
      document.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
      await nextTick()
      panel.value?.focus()
    } else {
      document.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
      if (lastFocused && typeof lastFocused.focus === 'function') lastFocused.focus()
    }
  }
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="sheet">
      <div
        v-if="modelValue"
        class="sheet-root"
        role="dialog"
        aria-modal="true"
        :aria-label="title || undefined"
      >
        <div class="sheet-overlay" @click="close"></div>

        <div ref="panel" class="sheet-panel" :style="{ '--sheet-w': width }" tabindex="-1">
          <header class="sheet-head">
            <h2 class="sheet-title">{{ title }}</h2>
            <button class="sheet-close" type="button" aria-label="关闭" @click="close">
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                aria-hidden="true"
              >
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </header>

          <div class="sheet-body">
            <slot />
          </div>

          <footer v-if="$slots.footer" class="sheet-foot">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.sheet-root {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--s5);
}

.sheet-overlay {
  position: absolute;
  inset: 0;
  background: rgba(16, 19, 17, 0.42);
}

.sheet-panel {
  position: relative;
  width: 100%;
  max-width: var(--sheet-w);
  max-height: calc(100vh - var(--s8));
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-2);
  overflow: hidden;
  outline: none;
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s4);
  padding: var(--s4) var(--s5);
  border-bottom: 1px solid var(--line);
}

.sheet-title {
  margin: 0;
  font-size: var(--fs-h3);
  font-weight: 600;
  color: var(--ink);
}

.sheet-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  transition: background 0.14s, color 0.14s;
}

.sheet-close:hover {
  background: var(--surface-2);
  color: var(--ink);
}

.sheet-body {
  padding: var(--s5);
  overflow-y: auto;
}

.sheet-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--s3);
  padding: var(--s4) var(--s5);
  border-top: 1px solid var(--line);
}

/* ---- 动效：入场 180ms，出场更快。只做位移 + 淡入，不做缩放 ---- */
.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.18s ease;
}

.sheet-enter-active .sheet-panel,
.sheet-leave-active .sheet-panel {
  transition: transform 0.18s ease;
}

.sheet-leave-active {
  transition-duration: 0.13s;
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}

.sheet-enter-from .sheet-panel,
.sheet-leave-to .sheet-panel {
  transform: translateY(12px);
}

/* ---- 移动端：从底部升起的面板 ---- */
@media (max-width: 640px) {
  .sheet-root {
    align-items: flex-end;
    padding: 0;
  }

  .sheet-panel {
    max-width: none;
    max-height: 88vh;
    border-radius: var(--r-lg) var(--r-lg) 0 0;
  }

  .sheet-enter-from .sheet-panel,
  .sheet-leave-to .sheet-panel {
    transform: translateY(100%);
  }
}
</style>
