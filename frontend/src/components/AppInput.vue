<script setup>
/**
 * AppInput —— 带标签的输入框。
 *
 * 三个刻意的设计决定：
 * 1. **标签始终可见**，不用 placeholder 代替标签 —— placeholder 一输入就消失，
 *    用户回头就不知道这一栏是什么。
 * 2. **错误贴近字段本身**，用「图标 + 文字」表达，不只靠红色 —— 色觉障碍用户也要能读到。
 * 3. 整体包在 <label> 里，点击标签任意位置都能聚焦，也是无障碍的原生关联方式。
 *
 * type="textarea" 时渲染多行输入（rows 控制初始高度）。多行样式与单行共用同一套
 * 边框/聚焦/错误态，避免两套输入框观感分叉。
 */
defineProps({
  modelValue: { type: [String, Number], default: '' },
  label: { type: String, default: '' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  /** 'text' | 'password' | 'email' | 'textarea' …（textarea 会渲染多行输入） */
  type: { type: String, default: 'text' },
  /** 仅多行模式生效：初始显示几行 */
  rows: { type: Number, default: 4 },
  placeholder: { type: String, default: '' },
  autocomplete: { type: String, default: 'off' },
  maxlength: { type: [String, Number], default: undefined },
  disabled: { type: Boolean, default: false },
  required: { type: Boolean, default: false }
})

defineEmits(['update:modelValue'])
</script>

<template>
  <label class="app-field" :class="{ 'is-disabled': disabled }">
    <span v-if="label" class="app-field__label">
      {{ label }}<span v-if="required" class="app-field__req" aria-hidden="true">*</span>
    </span>

    <span class="app-field__wrap">
      <!-- prefix 插槽：登录/注册页要"图标在框内"的样式，靠它复用本组件 -->
      <span v-if="$slots.prefix" class="app-field__prefix" aria-hidden="true">
        <slot name="prefix" />
      </span>

      <textarea
        v-if="type === 'textarea'"
        class="app-field__input app-field__input--area"
        :class="{ 'is-error': !!error }"
        :value="modelValue"
        :placeholder="placeholder"
        :rows="rows"
        :maxlength="maxlength"
        :disabled="disabled"
        :required="required"
        :aria-invalid="!!error"
        @input="$emit('update:modelValue', $event.target.value)"
      ></textarea>
      <input
        v-else
        class="app-field__input"
        :class="{ 'is-error': !!error, 'has-prefix': !!$slots.prefix }"
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :autocomplete="autocomplete"
        :maxlength="maxlength"
        :disabled="disabled"
        :required="required"
        :aria-invalid="!!error"
        @input="$emit('update:modelValue', $event.target.value)"
      />
    </span>

    <span v-if="error" class="app-field__msg app-field__msg--error">
      <svg
        width="13"
        height="13"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2.2"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7.5v5.5M12 16.5h.01" stroke-linecap="round" />
      </svg>
      {{ error }}
    </span>
    <span v-else-if="hint" class="app-field__msg">{{ hint }}</span>
  </label>
</template>

<style scoped>
.app-field {
  display: block;
}

.app-field__label {
  display: block;
  margin-bottom: var(--s2);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink);
}

.app-field__req {
  margin-left: 2px;
  color: var(--danger);
}

.app-field__wrap {
  position: relative;
  display: block;
}

.app-field__prefix {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  color: var(--ink-3);
  pointer-events: none;
}

.app-field__input {
  width: 100%;
  height: 42px;
  padding: 0 var(--s3);
  border: 1px solid var(--line-2);
  border-radius: var(--r-sm);
  background: var(--surface);
  font-family: inherit;
  font-size: var(--fs-body);
  color: var(--ink);
  outline: none;
  transition: border-color 0.14s, box-shadow 0.14s;
}

/* 多行：高度由 rows 决定，只允许纵向拉伸（横向拉伸会破坏栅格） */
.app-field__input--area {
  height: auto;
  min-height: 42px;
  padding: 10px var(--s3);
  line-height: 1.6;
  resize: vertical;
}

.app-field__input.has-prefix {
  padding-left: 38px;
}

.app-field__input::placeholder {
  color: #9aa09b;
}

.app-field__input:hover:not(:disabled) {
  border-color: var(--ink-3);
}

/* 聚焦用墨色描边：荧光色描边在近白底上几乎看不见 */
.app-field__input:focus {
  border-color: var(--ink);
  box-shadow: 0 0 0 3px rgba(16, 19, 17, 0.08);
}

.app-field__input.is-error {
  border-color: var(--danger);
}

.app-field__input:disabled {
  background: var(--surface-2);
  cursor: not-allowed;
}

.app-field__msg {
  display: flex;
  align-items: center;
  gap: var(--s1);
  margin-top: 6px;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.app-field__msg--error {
  color: var(--danger);
}

.is-disabled .app-field__label {
  color: var(--ink-3);
}
</style>
