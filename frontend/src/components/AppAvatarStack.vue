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
  <div v-if="users.length" class="stack-root" :aria-label="`共 ${users.length} 位球友`">
    <AppAvatar
      v-for="(u, i) in shown"
      :key="u.id ?? i"
      class="stack-root__item"
      :size="size"
      :src="u.avatar_url"
      :name="u.nickname"
      ring
    />
    <span
      v-if="rest"
      class="stack-root__item stack-root__more tnum"
      :style="{ width: size + 'px', height: size + 'px', fontSize: Math.round(size * 0.38) + 'px' }"
    >
      +{{ rest }}
    </span>
  </div>
</template>

<style scoped>
/* 根类名带命名空间：父页面的 .stack 会直接命中组件根元素（见 ActivityRow 的说明） */
.stack-root {
  display: flex;
  align-items: center;
}

.stack-root__item {
  margin-left: -8px;
}

.stack-root__item:first-child {
  margin-left: 0;
}

.stack-root__more {
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
