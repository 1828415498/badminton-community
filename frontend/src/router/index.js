import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 路由表。`meta.public: true` 的页面无需登录。
 *
 * 用 hash 模式（URL 带 #），好处是部署时不需要服务端配置 fallback，
 * 直接把 dist 丢给任意静态服务器就能跑。
 */
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
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/venues',
    children: [
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
        meta: { title: '活动详情' }
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
        meta: { title: '帖子详情' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人资料' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/venues' }
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

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 羽毛球社区` : '羽毛球社区'
})

export default router
