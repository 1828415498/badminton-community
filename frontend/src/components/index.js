/**
 * 组件与令牌的统一出口。
 *
 * 刻意不做「全局注册」：按需具名引入，依赖关系在文件顶部一眼可见，
 * 也避免把所有组件塞进首屏包。
 *
 *   import { AppButton, AppBadge } from '@/components'
 */
export { default as AppButton } from './AppButton.vue'
export { default as AppInput } from './AppInput.vue'
export { default as AppBadge } from './AppBadge.vue'
export { default as AppAvatar } from './AppAvatar.vue'
export { default as AppAvatarStack } from './AppAvatarStack.vue'
export { default as StatNumber } from './StatNumber.vue'
export { default as EmptyState } from './EmptyState.vue'
export { default as AppSkeleton } from './AppSkeleton.vue'
export { default as AppSheet } from './AppSheet.vue'
export { default as RallyArc } from './RallyArc.vue'
export { default as ActivityRow } from './ActivityRow.vue'
export { default as GameRow } from './GameRow.vue'
