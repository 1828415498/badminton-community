<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// 让 /activities/new、/activities/3 这类子路径都高亮到"活动"菜单
const activeMenu = computed(() => {
  const p = route.path
  const groups = ['/venues', '/reservations', '/activities', '/community', '/profile']
  return groups.find((g) => p.startsWith(g)) || p
})

async function handleLogout() {
  await store.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <el-container direction="vertical" class="app-shell">
    <el-header height="56px" class="app-header">
      <div class="header-inner">
        <div class="brand" @click="router.push('/venues')">
          <span class="brand-mark">羽</span>
          <span class="brand-text">羽毛球社区</span>
        </div>

        <el-menu :default-active="activeMenu" mode="horizontal" router class="nav-menu">
          <el-menu-item index="/venues">
            <el-icon><OfficeBuilding /></el-icon>场馆预约
          </el-menu-item>
          <el-menu-item index="/reservations">
            <el-icon><Calendar /></el-icon>我的预约
          </el-menu-item>
          <el-menu-item index="/activities">
            <el-icon><Flag /></el-icon>活动
          </el-menu-item>
          <el-menu-item index="/community">
            <el-icon><ChatDotRound /></el-icon>社区
          </el-menu-item>
        </el-menu>

        <el-dropdown @command="(cmd) => cmd === 'logout' ? handleLogout() : router.push('/profile')">
          <span class="user-chip">
            <el-avatar :size="26" :src="store.user?.avatar_url">
              {{ (store.user?.nickname || '?').slice(0, 1) }}
            </el-avatar>
            <span class="user-name">{{ store.user?.nickname }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon>个人资料
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
/* 整页渐变背景：顶部淡蓝紫 → 中段偏白 → 纯白。
   挂在 100vh 的容器上（内容在 .app-main 内部滚动），
   所以背景是固定的、不会随内容滚动被拉伸。 */
.app-shell {
  height: 100vh;
  background: linear-gradient(180deg, #c9d3f2 0%, #e3e9f9 30%, #ffffff 62%);
}

/* 顶部导航融入渐变：透明 + 去掉分割线 */
.app-header {
  background: transparent;
  border-bottom: none;
  padding: 0;
}

.header-inner {
  max-width: 1080px;
  height: 56px;
  margin: 0 auto;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
}

.brand-mark {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: #409eff;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-text {
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

.nav-menu {
  flex: 1;
  border-bottom: none;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  outline: none;
  padding: 4px 8px;
  border-radius: 6px;
}

.user-chip:hover {
  background: rgba(255, 255, 255, 0.7);
}

.user-name {
  font-size: 13px;
}

.app-main {
  padding: 0;
  overflow-y: auto;
  /* 背景透明，让 .app-shell 的整页渐变透出来 */
  background: transparent;
}
</style>
