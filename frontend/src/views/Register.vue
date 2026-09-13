<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'
import { AppInput, AppButton } from '@/components'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '', confirm: '', nickname: '' })
const errors = reactive({ username: '', nickname: '', password: '', confirm: '' })

/**
 * 逐字段校验，错误就近显示在该字段下方。
 * 规则以后端契约为准（RegisterRequest）：
 *   username 非空且 ≤64、nickname 非空且 ≤64、password ≥6。
 *
 * ⚠️ 注意不要在这里自造后端没有的规则（比如"用户名至少 3 位"）——
 * 那会造出"接口能过、界面过不了"的不一致。上一版登录页就有这个问题，已一并去掉。
 */
function validate() {
  errors.username = ''
  errors.nickname = ''
  errors.password = ''
  errors.confirm = ''

  const name = form.username.trim()
  const nick = form.nickname.trim()

  if (!name) errors.username = '请输入用户名'
  else if (name.length > 64) errors.username = '用户名不能超过 64 个字符'

  if (!nick) errors.nickname = '请输入昵称'
  else if (nick.length > 64) errors.nickname = '昵称不能超过 64 个字符'

  if (!form.password) errors.password = '请输入密码'
  else if (form.password.length < 6) errors.password = '密码至少 6 位'

  if (!form.confirm) errors.confirm = '请再输入一次密码'
  else if (form.confirm !== form.password) errors.confirm = '两次输入的密码不一致'

  return !errors.username && !errors.nickname && !errors.password && !errors.confirm
}

async function submit() {
  if (!validate()) return

  loading.value = true
  try {
    // 注册接口成功时不返回 token，所以注册完要回登录页再登一次
    await register({
      username: form.username.trim(),
      password: form.password,
      nickname: form.nickname.trim()
    })
    ElMessage.success('注册成功，请登录')
    // 把用户名带回去，登录页会自动填好
    router.push({ path: '/login', query: { username: form.username.trim() } })
  } catch (e) {
    // 具体失败原因（用户名已存在等）由 axios 拦截器统一提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="signup-page">
    <div class="signup-card">
      <div class="icon-tile">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round">
          <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M19 8v6M22 11h-6" />
        </svg>
      </div>

      <h1 class="title">创建账号</h1>
      <p class="subtitle">注册后就能报名球局、预约场地、和球友聊天</p>

      <div class="fields">
        <AppInput
          v-model="form.username"
          label="用户名"
          placeholder="登录用，注册后不可修改"
          autocomplete="username"
          :error="errors.username"
          required
        />

        <AppInput
          v-model="form.nickname"
          label="昵称"
          placeholder="展示给其他球友的名字"
          autocomplete="nickname"
          :error="errors.nickname"
          required
        />

        <AppInput
          v-model="form.password"
          label="密码"
          type="password"
          placeholder="至少 6 位"
          autocomplete="new-password"
          :error="errors.password"
          required
        />

        <AppInput
          v-model="form.confirm"
          label="确认密码"
          type="password"
          placeholder="再输入一次"
          autocomplete="new-password"
          :error="errors.confirm"
          required
          @keyup.enter="submit"
        />
      </div>

      <AppButton variant="primary" block :loading="loading" @click="submit">注册</AppButton>

      <div class="foot">
        <span>已有账号？</span>
        <button type="button" class="link" @click="router.push('/login')">返回登录</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 与 Login.vue 同一套视觉：平面白卡 + 发丝线 + 品牌色图标块。
   两个页面共用令牌与组件，不再各写一套硬编码颜色。 */

.signup-page {
  min-height: 100vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
  padding: var(--s5);
}

.signup-card {
  width: 100%;
  max-width: 400px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-1);
  padding: var(--s6);
  display: flex;
  flex-direction: column;
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
  align-self: center;
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
  display: flex;
  flex-direction: column;
  gap: var(--s4);
  margin-bottom: var(--s5);
}

.foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: var(--s4);
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.link {
  border: 0;
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: var(--fs-xs);
  color: var(--ink-2);
  text-decoration: underline;
  cursor: pointer;
}

.link:hover {
  color: var(--ink);
}
</style>
