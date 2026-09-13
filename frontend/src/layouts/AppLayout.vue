<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

/**
 * 自己判断高亮，不用 router-link 的 active-class。
 * 原因：`to="/"` 的 RouterLink，其 router-link-active 对**任何**路径都成立
 * （每条路径都以 / 开头），首页会永远显示为选中。
 * 另外 `/` 会 redirect 到 `/home`，所以首页要同时认这两个路径。
 */
function isActive(item) {
  if (item.to === '/home') return route.path === '/home' || route.path === '/'
  return route.path === item.to || route.path.startsWith(item.to + '/')
}

/*
  导航只放 4 项，对应产品真正要用户做的三件事：
  发现球局（球局）→ 自己开局（场地）→ 认识球友（社区），首页是它们的总入口。

  刻意去掉了「我的预约」：它与「个人资料」同属"我的 ×"，收进右上角用户菜单。
  改造前是 5 个平级入口 + 用户菜单，桌面端和移动端都出现"哪个都一样重、
  不知道该看哪"的问题 —— 这是"所有模块视觉权重一样"的结构性来源。
  移动端底部 Tab 也刚好是 4 项，是拇指操作最舒服的数量。
*/
const NAV = [
  { to: '/home', label: '首页', icon: 'HomeFilled' },
  { to: '/activities', label: '球局', icon: 'Trophy' },
  { to: '/venues', label: '场地', icon: 'Grid' },
  { to: '/community', label: '社区', icon: 'ChatLineSquare' }
]

const MENU = [
  { cmd: 'reservations', label: '我的预约', icon: 'Calendar' },
  { cmd: 'profile', label: '个人资料', icon: 'User' }
]

async function handleLogout() {
  await store.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

function handleCommand(cmd) {
  if (cmd === 'logout') handleLogout()
  else if (cmd === 'reservations') router.push('/reservations')
  else router.push('/profile')
}
</script>

<template>
  <a class="skip-link" href="#main">跳到主要内容</a>

  <el-container direction="vertical" class="app-shell">
    <el-header height="60px" class="app-header">
      <div class="header-inner">
        <div class="brand" @click="router.push('/')">
          <!-- 球场俯视图：品牌色地面 + 墨色场线（中网 / 前后发球线 / 中线） -->
          <svg class="brand-mark" viewBox="0 0 28 28" width="28" height="28" aria-hidden="true">
            <rect width="28" height="28" rx="8" fill="currentColor" />
            <g stroke="#101311" fill="none" stroke-width="1.1" opacity="0.9">
              <rect x="5.5" y="6" width="17" height="16" rx="1" />
              <line x1="5.5" y1="14" x2="22.5" y2="14" stroke-width="1.5" />
              <line x1="5.5" y1="10" x2="22.5" y2="10" />
              <line x1="5.5" y1="18" x2="22.5" y2="18" />
              <line x1="14" y1="6" x2="14" y2="10" />
              <line x1="14" y1="18" x2="14" y2="22" />
            </g>
          </svg>
          <span class="brand-text">羽毛球社区</span>
        </div>

        <nav class="nav" aria-label="主导航">
          <router-link
            v-for="item in NAV"
            :key="item.to"
            :to="item.to"
            class="nav-item"
            :class="{ 'is-active': isActive(item) }"
          >
            <el-icon :size="15"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>

        <el-dropdown @command="handleCommand">
          <button class="user-chip" type="button">
            <el-avatar :size="26" :src="store.user?.avatar_url">
              {{ (store.user?.nickname || '?').slice(0, 1) }}
            </el-avatar>
            <span class="user-name">{{ store.user?.nickname }}</span>
            <el-icon :size="12"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="m in MENU" :key="m.cmd" :command="m.cmd">
                <el-icon><component :is="m.icon" /></el-icon>{{ m.label }}
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-main id="main" class="app-main">
      <router-view />
    </el-main>

    <!-- 移动端底部 Tab：桌面隐藏。
         横向滚动的顶部导航在手机上等于"导航项消失"，必须换成拇指够得到的底部栏。 -->
    <nav class="tabbar" aria-label="底部导航">
      <router-link
        v-for="item in NAV"
        :key="item.to"
        :to="item.to"
        class="tabbar__item"
        :class="{ 'is-active': isActive(item) }"
      >
        <el-icon :size="20"><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </router-link>
    </nav>
  </el-container>
</template>

<style scoped>
/* 背景：近白纯色。
   上一版的淡蓝紫渐变属于「AI SaaS 风格」，与新方向（去渐变、去玻璃拟态）冲突，
   已按 docs/frontend-design.md §3 移除。 */
.app-shell {
  height: 100vh;
  background: var(--bg);
}

/* 顶部导航：实体白面 + 一条发丝线。
   去掉了毛玻璃（backdrop-filter）—— 那也是被点名的 AI 味元素之一。 */
.app-header {
  padding: 0;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.header-inner {
  max-width: 1120px;
  height: 60px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 28px;
}

/* ---- 品牌 ---- */
.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  flex-shrink: 0;
}

.brand-mark {
  color: var(--brand);
  flex-shrink: 0;
}

.brand-text {
  font-size: var(--fs-body);
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--ink);
  white-space: nowrap;
}

/* ---- 导航 ---- */
.nav {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  overflow-x: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border-radius: 999px;
  font-size: 14px;
  color: var(--ink-2);
  white-space: nowrap;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background: var(--surface-2);
  color: var(--ink);
}

/* 当前页：品牌色浅底 + 墨色文字 + 字重。
   品牌色是荧光青柠，绝不能当文字用（在白底上对比度不足 3:1），
   所以这里是"浅底 + 墨字"，而不是"品牌色文字"。
   同时用字重做第二重标记 —— 不靠颜色单独传递信息。 */
.nav-item.is-active {
  background: var(--brand-200);
  color: var(--ink);
  font-weight: 600;
}

/* ---- 用户区 ---- */
.user-chip {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 4px 10px 4px 5px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.user-chip:hover {
  background: var(--surface-2);
  border-color: var(--line);
}

.user-name {
  font-size: var(--fs-sm);
  color: var(--ink-2);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-main {
  padding: 0;
  overflow-y: auto;
  background: transparent;
}

/* ---- 移动端底部 Tab（桌面隐藏） ---- */
.tabbar {
  display: none;
}

@media (max-width: 640px) {
  /* 顶部横向导航在手机上会溢出，直接换成底部栏 */
  .nav {
    display: none;
  }

  .header-inner {
    gap: var(--s3);
    justify-content: space-between;
    padding: 0 var(--s4);
  }

  .tabbar {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 50;
    background: var(--surface);
    border-top: 1px solid var(--line);
    padding-bottom: env(safe-area-inset-bottom, 0);
  }

  .tabbar__item {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3px;
    height: 54px;
    font-size: var(--fs-xs);
    color: var(--ink-3);
    text-decoration: none;
  }

  /* 当前页：品牌色指示条 + 字重，不靠颜色单独传递 */
  .tabbar__item.is-active {
    color: var(--ink);
    font-weight: 600;
  }

  .tabbar__item.is-active::before {
    content: '';
    position: absolute;
    top: 0;
    left: 50%;
    transform: translateX(-50%);
    width: 24px;
    height: 3px;
    border-radius: 0 0 3px 3px;
    background: var(--brand);
  }

  /* 给底部栏留出空间，否则最后一块内容会被盖住 */
  .app-main {
    padding-bottom: 54px;
  }
}
</style>
