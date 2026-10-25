<template>
  <el-container class="dp-layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="dp-aside">
      <div class="dp-logo" @click="$router.push('/dashboard')">
        <span class="dp-logo-icon">🛠️</span>
        <span v-if="!collapsed" class="dp-logo-text">DevPilot</span>
      </div>
      <el-menu :default-active="$route.path" router :collapse="collapsed" class="dp-menu">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon><template #title>仪表盘</template>
        </el-menu-item>
        <el-menu-item index="/projects">
          <el-icon><FolderOpened /></el-icon><template #title>项目管理</template>
        </el-menu-item>
        <el-menu-item index="/search">
          <el-icon><Search /></el-icon><template #title>代码检索</template>
        </el-menu-item>
        <el-menu-item index="/qa">
          <el-icon><ChatDotRound /></el-icon><template #title>AI 问答</template>
        </el-menu-item>
        <el-menu-item index="/bug">
          <el-icon><Warning /></el-icon><template #title>Bug 分析</template>
        </el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin">
          <el-icon><Setting /></el-icon><template #title>系统设置</template>
        </el-menu-item>
      </el-menu>
      <div class="dp-collapse" @click="collapsed = !collapsed">
        <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
      </div>
    </el-aside>

    <el-container>
      <el-header class="dp-header">
        <div class="dp-header-title">{{ $route.meta.title || 'DevPilot' }}</div>
        <el-dropdown @command="onCommand">
          <span class="dp-user">
            <el-avatar :size="28" style="background: var(--dp-primary)">
              {{ (auth.user?.nickname || 'U').slice(0, 1) }}
            </el-avatar>
            <span class="dp-user-name">{{ auth.user?.nickname }}</span>
            <el-tag v-if="auth.isAdmin" size="small" type="warning" effect="plain">管理员</el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="dp-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const collapsed = ref(false)

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    await auth.logout()
    router.push({ name: 'login' })
  }
}
</script>

<style scoped>
.dp-layout {
  height: 100vh;
}
.dp-aside {
  background: #001529;
  display: flex;
  flex-direction: column;
  transition: width 0.2s;
}
.dp-logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  color: #fff;
  cursor: pointer;
  font-weight: 700;
  font-size: 16px;
}
.dp-menu {
  border-right: none;
  flex: 1;
  background: transparent;
}
.dp-menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.65);
}
.dp-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: var(--dp-primary);
}
.dp-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
}
.dp-collapse {
  padding: 14px;
  color: rgba(255, 255, 255, 0.65);
  cursor: pointer;
  text-align: center;
}
.dp-collapse:hover {
  color: #fff;
}
.dp-header {
  background: #fff;
  border-bottom: 1px solid var(--dp-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
}
.dp-header-title {
  font-size: 15px;
  font-weight: 600;
}
.dp-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.dp-user-name {
  font-size: 13px;
}
.dp-main {
  padding: 0;
  overflow: auto;
}
</style>
