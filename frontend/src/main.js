import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles.css'

const app = createApp(App)

// 全量注册 Element Plus 图标，模板里可直接用 <el-icon><Search /></el-icon>
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

/**
 * 兜住"已经提示过用户、但页面没写 catch"的接口错误。
 *
 * 背景：axios 拦截器失败时会 reject，页面若只有 try/finally 而没 catch，
 * 这个拒绝就升级为未处理的 Promise 拒绝，控制台多一条红色报错 ——
 * 但用户其实已经看到 ElMessage 了，这不是程序 bug。
 *
 * 只吞带 __toasted 标记的（见 api/request.js），
 * 真正的 JS 异常（TypeError、undefined 访问等）照旧暴露，不会被掩盖。
 */
window.addEventListener('unhandledrejection', (event) => {
  if (event.reason && event.reason.__toasted) {
    event.preventDefault()
  }
})

app.mount('#app')
