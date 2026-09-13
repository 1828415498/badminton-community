<script setup>
/**
 * StatNumber —— 大数字。
 *
 * 这是本项目的第三个视觉母题。运动产品里最有说服力的排版元素不是图，
 * 是数字：「还差 2 人」比任何进度条都直接回答了"我能不能去"。
 *
 * 为什么 brand 强调不用品牌色文字：
 * 荧光青柠在白底上对比度不足 3:1，当文字读不清。
 * 所以用「荧光笔划线」—— 数字保持墨色，底下压一条品牌色块。
 * 既拿到了品牌识别，又保住了对比度。
 *
 * 数字一律用等宽字形（tabular-nums）：多行排列时小数点对齐，扫读快很多。
 */
defineProps({
  value: { type: [String, Number], required: true },
  unit: { type: String, default: '' },
  label: { type: String, default: '' },
  /**
   * 'below' | 'before'
   *
   * below —— 标签在数字下一行（报表/看板里的常规排版，如「总场次 / 128」）。
   * before —— 标签在数字**前面同一行**。中文里「还差 7 人」是一个固定语序，
   *          标签放到下一行会读成「7 人 / 还差」，得从下往上读才对 ——
   *          球局行必须用 before。
   */
  labelPosition: { type: String, default: 'below' },
  /** md 26px / lg 40px / xl 56px */
  size: { type: String, default: 'md' },
  /** ink 常规 / brand 荧光笔强调 / quiet 弱化 */
  tone: { type: String, default: 'ink' }
})
</script>

<template>
  <div class="app-stat" :class="[`app-stat--${size}`, `app-stat--${tone}`]">
    <div class="app-stat__row">
      <span v-if="label && labelPosition === 'before'" class="app-stat__pre">{{ label }}</span>
      <span class="app-stat__value tnum">{{ value }}</span>
      <span v-if="unit" class="app-stat__unit">{{ unit }}</span>
    </div>
    <p v-if="label && labelPosition === 'below'" class="app-stat__label">{{ label }}</p>
  </div>
</template>

<style scoped>
.app-stat__row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.app-stat__value {
  font-family: var(--font-display);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.05;
  color: var(--ink);
}

.app-stat__unit {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-3);
}

/* labelPosition="before"：与数字同基线，中文语序「还差 7 人」 */
.app-stat__pre {
  font-size: var(--fs-sm);
  color: var(--ink-3);
  white-space: nowrap;
}

.app-stat__label {
  margin: 2px 0 0;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.app-stat--md .app-stat__value {
  font-size: var(--fs-h2);
}

.app-stat--lg .app-stat__value {
  font-size: var(--fs-stat);
}

.app-stat--xl .app-stat__value {
  font-size: 56px;
}

.app-stat--quiet .app-stat__value {
  color: var(--ink-3);
}

/* 荧光笔划线：inset 阴影压在文字下半部，形成"高亮"效果。
   比整块底色克制，也不会把数字变成"按钮"。 */
.app-stat--brand .app-stat__value {
  box-shadow: inset 0 -0.34em 0 var(--brand);
}
</style>
