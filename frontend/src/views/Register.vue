<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '', confirm: '', nickname: '' })

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 64, message: '不能超过 64 个字符', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 64, message: '不能超过 64 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) =>
        value === form.password ? cb() : cb(new Error('两次输入的密码不一致')),
      trigger: 'blur'
    }
  ]
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    // 注意：注册接口成功时不返回 token（api.md §2.1），必须再登录一次
    await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname
    })
    ElMessage.success('注册成功，请登录')
    router.push({ path: '/login', query: { username: form.username } })
  } catch (e) {
    // 错误提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrap">
    <el-card class="auth-card" shadow="never">
      <div class="auth-head">
        <span class="brand-mark">羽</span>
        <h1 class="auth-title">创建账号</h1>
        <p class="muted">加入羽毛球社区</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="登录用，唯一" size="large" />
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="展示给其他球友的名字" size="large" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="至少 6 位"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirm">
          <el-input
            v-model="form.confirm"
            type="password"
            placeholder="再次输入密码"
            size="large"
            show-password
          />
        </el-form-item>

        <el-button type="primary" size="large" class="full-btn" :loading="loading" @click="submit">
          注册
        </el-button>
      </el-form>

      <div class="auth-foot">
        <span class="muted">已有账号？</span>
        <el-link type="primary" @click="router.push('/login')">返回登录</el-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #eef4ff 0%, #f5f7fa 60%);
  padding: 20px;
}

.auth-card {
  width: 100%;
  max-width: 400px;
  border-radius: 12px;
}

.auth-head {
  text-align: center;
  margin-bottom: 18px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 11px;
  background: #409eff;
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 12px;
}

.auth-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 4px;
}

.full-btn {
  width: 100%;
}

.auth-foot {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
}
</style>
