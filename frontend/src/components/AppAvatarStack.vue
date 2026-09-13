<script setup>
/**
 * AppAvatarStack —— 头像堆叠。
 *
 * 活动卡片上用它回答"都有谁打" —— 这是"我能不能去"之外最重要的社交信号。
 * 超出 max 的部分不省略成"…"，而是明确写「+3」，让人知道还有几个人。
 */
import { computed } from 'vue'
import AppAvatar from './AppAvatar.vue'

const props = defineProps({
  /** [{ nickname, avatar_url }] */
  users: { type: Array, default: () => [] },
  max: { type: Number, default: 4 },
  size: { type: Number, default: 28 }
})

const shown = computed(() => props.users.slice(0, props.max))
const rest = computed(() => Math.max(0, props.users.length - props.max))
</script>

<template>
  <div v-if="users.length" class="stack" :aria-label="`共 ${users.length} 位球友`">
    <AppAvatar
      v-for="(u, i) in shown"
      :key="u.id ?? i"
      class="stack__item"
      :size="size"
      :src="u.avatar_url"
      :name="u.nickname"
      ring
    />
    <span
      v-if="rest"
      class="stack__item stack__more tnum"
      :style="{ width: size + 'px', height: size + 'px', fontSize: Math.round(size * 0.38) + 'px' }"
    >
      +{{ rest }}
    </span>
  </div>
</template>

<style scoped>
.stack {
  display: flex;
  align-items: center;
}

.stack__item {
  margin-left: -8px;
}

.stack__item:first-child {
  margin-left: 0;
}

.stack__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--ink-3);
  font-weight: 600;
  box-shadow: 0 0 0 2px var(--surface);
}
</style>
