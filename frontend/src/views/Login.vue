<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { AppInput, AppButton } from '@/components'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const form = reactive({ username: '', password: '' })
const errors = reactive({ username: '', password: '' })
const loading = ref(false)

// 从注册页跳过来时带着用户名，直接填好，少打一次字
onMounted(() => {
  if (route.query.username) form.username = String(route.query.username)
})

/**
 * 校验规则以后端契约为准（LoginRequest：两者都只要求非空）。
 * ⚠️ 上一版这里有一条"用户名至少 3 个字符"——那是前端**自造**的规则，
 * 后端并没有。自造规则会造出"接口能过、界面过不了"的不一致，已去掉。
 *
 * 错误逐字段就近显示，而不是笼统地报一句"请输入用户名和密码"。
 */
function validate() {
  errors.username = form.username.trim() ? '' : '请输入用户名'
  errors.password = form.password ? '' : '请输入密码'
  return !errors.username && !errors.password
}

async function submit() {
  if (!validate()) return

  loading.value = true
  try {
    await store.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/')
  } catch (e) {
    // 具体失败原因（凭据错误等）已由 axios 拦截器统一提示
  } finally {
    loading.value = false
  }
}

function fillDemo() {
  form.username = 'admin123'
  form.password = '123456'
  errors.username = ''
  errors.password = ''
}
</script>

<template>
  <div class="signin-page">
    <div class="signin-card">
      <!-- 顶部图标块：品牌色填充 + 墨色图标，第一屏最直接的品牌露出 -->
      <div class="icon-tile">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round">
          <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
          <polyline points="10 17 15 12 10 7" />
          <line x1="15" y1="12" x2="3" y2="12" />
        </svg>
      </div>

      <h1 class="title">欢迎回来</h1>
      <p class="subtitle">登录后就能找到下一场球局，认识常来这个球馆的人</p>

      <div class="fields">
        <AppInput
          v-model="form.username"
          placeholder="用户名"
          autocomplete="username"
          :error="errors.username"
          @keyup.enter="submit"
        >
          <template #prefix>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
          </template>
        </AppInput>

        <AppInput
          v-model="form.password"
          type="password"
          placeholder="密码"
          autocomplete="current-password"
          :error="errors.password"
          @keyup.enter="submit"
        >
          <template #prefix>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
          </template>
        </AppInput>

        <div class="row">
          <button type="button" class="link" @click="router.push('/register')">
            还没有账号？注册
          </button>
        </div>
      </div>

      <AppButton variant="primary" block :loading="loading" @click="submit">登 录</AppButton>

      <div class="divider">
        <span class="divider-line" />
        <span class="divider-text">或者</span>
        <span class="divider-line" />
      </div>

      <AppButton variant="outline" block @click="fillDemo">填入演示账号</AppButton>

      <p class="foot-hint">开发环境初始账号 admin123 / 123456</p>
    </div>
  </div>
</template>

<style scoped>
/* 与 Register.vue 同一套视觉；输入框与按钮已由 AppInput / AppButton 提供，
   这里只保留卡片骨架，不再重复定义字段样式。 */

.signin-page {
  min-height: 100vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
  padding: var(--s5);
}

.signin-card {
  width: 100%;
  max-width: 384px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-1);
  padding: var(--s7) var(--s6) var(--s6);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.icon-tile {
  width: 52px;
  height: 52px;
  border-radius: var(--r-md);
  background: var(--brand);
  color: var(--brand-ink);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--s5);
}

.icon-tile svg {
  width: 26px;
  height: 26px;
}

.title {
  font-size: var(--fs-h2);
  font-weight: 700;
  letter-spacing: -0.015em;
  color: var(--ink);
  margin: 0 0 var(--s2);
  text-align: center;
}

.subtitle {
  font-size: var(--fs-sm);
  color: var(--ink-3);
  line-height: 1.55;
  margin: 0 0 var(--s5);
  text-align: center;
}

.fields {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--s3);
  margin-bottom: var(--s4);
}

/* 注册入口单独一行靠右 */
.row {
  display: flex;
  justify-content: flex-end;
}

.link {
  border: 0;
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: var(--fs-xs);
  color: var(--ink-3);
  cursor: pointer;
  transition: color 0.14s;
}

.link:hover {
  color: var(--ink);
  text-decoration: underline;
}

/* 分隔与次级动作 */
.divider {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--s2);
  margin: var(--s5) 0 var(--s4);
}

.divider-line {
  flex: 1;
  border-top: 1px dashed var(--line-2);
}

.divider-text {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.foot-hint {
  margin: var(--s5) 0 0;
  font-size: var(--fs-xs);
  color: var(--ink-3);
  text-align: center;
}
</style>
