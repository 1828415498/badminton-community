<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const form = reactive({ username: '', password: '' })
const error = ref('')
const loading = ref(false)

/**
 * 轻量前端校验：只挡明显的手误（空值 / 太短），
 * 真正的判定（用户名不存在、密码错误）由后端负责。
 */
function validate() {
  if (!form.username || !form.password) {
    error.value = '请输入用户名和密码'
    return false
  }
  if (form.username.trim().length < 3) {
    error.value = '用户名至少 3 个字符'
    return false
  }
  if (form.password.length < 6) {
    error.value = '密码至少 6 位'
    return false
  }
  error.value = ''
  return true
}

async function submit() {
  if (!validate()) return

  loading.value = true
  try {
    await store.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/venues')
  } catch (e) {
    // 具体失败原因（凭据错误等）已由 axios 拦截器统一提示
  } finally {
    loading.value = false
  }
}

function fillDemo() {
  form.username = 'admin123'
  form.password = '123456'
  error.value = ''
}
</script>

<template>
  <div class="signin-page">
    <div class="signin-card">
      <!-- 顶部图标块 -->
      <div class="icon-tile">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round">
          <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
          <polyline points="10 17 15 12 10 7" />
          <line x1="15" y1="12" x2="3" y2="12" />
        </svg>
      </div>

      <h2 class="title">欢迎回来</h2>
      <p class="subtitle">登录后即可预约场地、报名活动、和球友交流</p>

      <div class="fields">
        <div class="field">
          <span class="field-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
          </span>
          <input
            v-model="form.username"
            class="input"
            placeholder="用户名"
            autocomplete="username"
            @keyup.enter="submit"
          />
        </div>

        <div class="field">
          <span class="field-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
          </span>
          <input
            v-model="form.password"
            class="input"
            type="password"
            placeholder="密码"
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </div>

        <div class="error-row">
          <span v-if="error" class="error-text">{{ error }}</span>
          <button type="button" class="link-btn" @click="router.push('/register')">
            还没有账号？注册
          </button>
        </div>
      </div>

      <button type="button" class="submit-btn" :disabled="loading" @click="submit">
        {{ loading ? '登录中…' : '登 录' }}
      </button>

      <div class="divider">
        <span class="divider-line" />
        <span class="divider-text">或者</span>
        <span class="divider-line" />
      </div>

      <button type="button" class="demo-btn" @click="fillDemo">填入演示账号</button>

      <p class="foot-hint">开发环境初始账号 admin123 / 123456</p>
    </div>
  </div>
</template>

<style scoped>
/* ── 页面：纯白底 + 居中 ───────────────────────────────── */
.signin-page {
  min-height: 100vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  padding: 20px;
  box-sizing: border-box;
}

/* ── 卡片：淡蓝→白渐变 + 24px 圆角 + 大阴影 ─────────────── */
.signin-card {
  width: 100%;
  max-width: 384px;
  background: linear-gradient(to bottom, rgba(240, 249, 255, 0.5), #ffffff);
  border: 1px solid #dbeafe;
  border-radius: 24px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
  padding: 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-sizing: border-box;
}

.icon-tile {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1);
  margin-bottom: 24px;
}

.icon-tile svg {
  width: 28px;
  height: 28px;
  color: #111827;
}

.title {
  font-size: 24px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 8px;
  text-align: center;
}

.subtitle {
  font-size: 14px;
  color: #6b7280;
  line-height: 1.5;
  margin: 0 0 24px;
  text-align: center;
}

/* ── 输入区 ──────────────────────────────────────────── */
.fields {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 8px;
}

.field {
  position: relative;
}

.field-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #9ca3af;
  display: flex;
  pointer-events: none;
}

.field-icon svg {
  width: 16px;
  height: 16px;
}

.input {
  width: 100%;
  padding: 9px 12px 9px 40px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  font-size: 14px;
  color: #111827;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}

.input::placeholder {
  color: #9ca3af;
}

.input:focus {
  background: #ffffff;
  border-color: #bfdbfe;
  box-shadow: 0 0 0 3px rgba(191, 219, 254, 0.6);
}

/* 错误提示与注册入口同一行：错误靠左、链接靠右 */
.error-row {
  width: 100%;
  min-height: 18px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.error-text {
  font-size: 13px;
  color: #ef4444;
  margin-right: auto;
}

.link-btn {
  font-size: 12px;
  color: #6b7280;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  font-family: inherit;
}

.link-btn:hover {
  color: #111827;
  text-decoration: underline;
}

/* ── 主按钮：深灰渐变 ─────────────────────────────────── */
.submit-btn {
  width: 100%;
  margin: 8px 0 16px;
  padding: 10px 0;
  border: none;
  border-radius: 12px;
  background: linear-gradient(to bottom, #374151, #111827);
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  font-family: inherit;
  letter-spacing: 2px;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06);
  transition: filter 0.15s;
}

.submit-btn:hover:not(:disabled) {
  filter: brightness(1.08);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ── 虚线分隔 ────────────────────────────────────────── */
.divider {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
}

.divider-line {
  flex: 1;
  border-top: 1px dashed #e5e7eb;
}

.divider-text {
  font-size: 12px;
  color: #9ca3af;
}

/* ── 次级按钮 ────────────────────────────────────────── */
.demo-btn {
  width: 100%;
  margin-top: 8px;
  padding: 9px 0;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  font-size: 13px;
  color: #4b5563;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s;
}

.demo-btn:hover {
  background: #f3f4f6;
}

.foot-hint {
  margin: 16px 0 0;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
}
</style>
