import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 路由表。`meta.public: true` 的页面无需登录。
 *
 * 用 hash 模式（URL 带 #），好处是部署时不需要服务端配置 fallback，
 * 直接把 dist 丢给任意静态服务器就能跑。
 */
// 组件与令牌的检阅页 —— 只在开发环境注册，生产构建里不存在这个路由
const devRoutes = import.meta.env.DEV
  ? [
      {
        path: 'dev/ui',
        name: 'dev-ui',
        component: () => import('@/views/DevUI.vue'),
        meta: { title: 'Design System' }
      }
    ]
  : []

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/Register.vue'),
    meta: { public: true, title: '注册' }
  },
  // ---- 报表打印页 ----
  // 故意放在 AppLayout 之外：这两页是"一张纸"，页面上没有导航栏与底部 Tab，
  // 打印时不需要再用 CSS 把一堆界面元素藏起来。权限照旧走下面的登录守卫，
  // 真正的数据权限由后端 Service 判定。
  {
    path: '/print/reservation/:id',
    name: 'print-reservation',
    component: () => import('@/views/ReservationReceipt.vue'),
    meta: { title: '预约凭证' }
  },
  {
    path: '/print/activity/:id/roster',
    name: 'print-activity-roster',
    component: () => import('@/views/ActivityRoster.vue'),
    meta: { title: '活动报名名单' }
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'venues',
        name: 'venues',
        component: () => import('@/views/Venues.vue'),
        meta: { title: '场馆预约' }
      },
      {
        path: 'reservations',
        name: 'reservations',
        component: () => import('@/views/MyReservations.vue'),
        meta: { title: '我的预约' }
      },
      {
        path: 'activities',
        name: 'activities',
        component: () => import('@/views/Activities.vue'),
        meta: { title: '活动列表' }
      },
      {
        path: 'activities/new',
        name: 'activity-new',
        component: () => import('@/views/NewActivity.vue'),
        meta: { title: '发起活动' }
      },
      {
        path: 'activities/mine',
        name: 'activities-mine',
        component: () => import('@/views/MyActivities.vue'),
        meta: { title: '我的活动' }
      },
      {
        path: 'activities/:id',
        name: 'activity-detail',
        component: () => import('@/views/ActivityDetail.vue'),
        // hideTabBar：这一页自己带固定主操作栏，移动端不再叠加底部 Tab
        meta: { title: '球局详情', hideTabBar: true }
      },
      {
        path: 'community',
        name: 'community',
        component: () => import('@/views/Community.vue'),
        meta: { title: '社区' }
      },
      {
        path: 'community/:id',
        name: 'post-detail',
        component: () => import('@/views/PostDetail.vue'),
        // 暂不加 hideTabBar：这页目前还没有自己的固定操作栏，
        // 隐藏底部 Tab 会让移动端失去导航出口。等重做该页时一并加上。
        meta: { title: '帖子详情' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人资料' }
      },
      {
        path: 'admin',
        name: 'admin',
        component: () => import('@/views/Admin.vue'),
        // requiresAdmin：非管理员访问会被守卫送回首页。
        // 注意这只是体验层面的一道闸，真正的授权判定在后端 Service（认证与授权分离）。
        meta: { title: '管理台', requiresAdmin: true }
      },
      ...devRoutes
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to) => {
  const store = useUserStore()

  if (to.meta.public) {
    return true
  }

  if (!store.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 刷新页面后 store 是空的，这里补一次「我是谁」
  if (!store.user) {
    await store.fetchMe()
  }
  if (!store.user) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 管理台：非管理员直接送回首页（后端仍会独立校验一次，这里不构成安全边界）
  if (to.meta.requiresAdmin && store.user.role !== 1) {
    return { path: '/home' }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 羽毛球社区` : '羽毛球社区'
})

export default router
