<script setup>
/**
 * AppAvatar —— 头像。
 *
 * 后端 avatar_url 为空时不要显示破图，退回昵称首字。
 * 圆形是"人"的通用识别形状，这里的圆角是有语义的，不是"过度圆角"。
 */
import { computed } from 'vue'

const props = defineProps({
  size: { type: Number, default: 36 },
  src: { type: String, default: '' },
  name: { type: String, default: '' },
  /** 叠在有底色/图片上时加一圈描边，避免和背景糊在一起 */
  ring: { type: Boolean, default: false }
})

const initial = computed(() => (props.name || '?').trim().slice(0, 1) || '?')
const hasImage = computed(() => !!props.src)
</script>

<template>
  <img
    v-if="hasImage"
    class="app-avatar"
    :class="{ 'has-ring': ring }"
    :src="src"
    :alt="name || '球友头像'"
    :style="{ width: size + 'px', height: size + 'px', fontSize: Math.round(size * 0.42) + 'px' }"
  />
  <span
    v-else
    class="app-avatar app-avatar--initial"
    :class="{ 'has-ring': ring }"
    :style="{ width: size + 'px', height: size + 'px', fontSize: Math.round(size * 0.42) + 'px' }"
    :aria-label="name || '球友'"
    role="img"
  >
    {{ initial }}
  </span>
</template>

<style scoped>
.app-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  object-fit: cover;
  background: var(--surface-2);
  color: var(--ink-2);
  font-weight: 600;
  line-height: 1;
  overflow: hidden;
}

.has-ring {
  box-shadow: 0 0 0 2px var(--surface);
}
</style>
