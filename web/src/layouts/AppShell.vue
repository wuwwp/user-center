<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { roleLabel } from '@/labels'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

async function handleLogout(): Promise<void> {
  await auth.logout()
  ElMessage.success('已退出登录')
  await router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="app-shell">
    <el-aside class="app-aside" width="224px">
      <div class="app-brand">
        <span class="brand-mark">UC</span>
        <span class="brand-text">用户中心</span>
      </div>
      <el-menu :default-active="route.path" class="app-menu" router>
        <el-menu-item index="/profile">个人中心</el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin">用户管理</el-menu-item>
      </el-menu>
      <p class="aside-footnote">统一账号 · 安全登录</p>
    </el-aside>

    <el-container>
      <el-header class="app-header" height="64px">
        <div class="mobile-brand">用户中心</div>
        <nav class="mobile-nav">
          <router-link to="/profile">个人中心</router-link>
          <router-link v-if="auth.isAdmin" to="/admin">用户管理</router-link>
        </nav>

        <div class="header-actions">
          <div class="header-user">
            <span class="header-user-name">
              {{ auth.user?.nickname || auth.user?.userAccount || '未登录' }}
            </span>
            <span class="header-user-role">{{ roleLabel(auth.user?.role) }}</span>
          </div>
          <el-button text class="logout-button" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="app-main">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.app-aside {
  display: flex;
  flex-direction: column;
  background: var(--uc-ink);
  color: #f5f1e8;
  padding: 24px 16px;
}

.app-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px 24px;
}

.brand-mark {
  display: inline-grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--uc-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.brand-text {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.app-menu {
  border-right: none;
  background: transparent;
}

.app-menu :deep(.el-menu-item) {
  color: #cfc8ba;
  border-radius: 8px;
  margin-bottom: 4px;
}

.app-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.app-menu :deep(.el-menu-item.is-active) {
  background: var(--uc-primary);
  color: #fff;
}

.aside-footnote {
  margin-top: auto;
  padding: 0 8px;
  color: #8b8478;
  font-size: 12px;
  line-height: 1.6;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--uc-line);
  background: var(--uc-surface);
  padding: 0 32px;
}

.mobile-brand,
.mobile-nav {
  display: none;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.header-user {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.25;
}

.header-user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--uc-ink);
}

.header-user-role {
  font-size: 12px;
  color: var(--uc-muted);
}

.logout-button {
  color: var(--uc-muted);
}

.app-main {
  padding: 32px;
  background: var(--uc-bg);
}

@media (max-width: 767px) {
  .app-aside {
    display: none;
  }

  .app-header {
    height: auto !important;
    padding: 12px 16px;
    flex-wrap: wrap;
  }

  .mobile-brand {
    display: block;
    font-weight: 700;
    color: var(--uc-ink);
  }

  .mobile-nav {
    display: flex;
    gap: 16px;
    order: 3;
    width: 100%;
    padding-top: 8px;
  }

  .mobile-nav a {
    color: var(--uc-muted);
    text-decoration: none;
    font-size: 14px;
  }

  .mobile-nav a.router-link-active {
    color: var(--uc-primary);
    font-weight: 600;
  }

  .app-main {
    padding: 20px 16px;
  }
}
</style>
