<template>
  <header class="header">
    <div class="left" @click="$router.push('/')">
      <div class="logo">💬</div>
      <span class="brand">ChatPartner</span>
    </div>
    <nav class="nav">
      <a :class="{ active: $route.name === 'home' }" @click.prevent="$router.push({ name: 'home' })">首页</a>
      <a :class="{ active: String($route.name || '').indexOf('roles') === 0 }" @click.prevent="$router.push({ name: 'roles' })">人物大全</a>
      <a :class="{ active: String($route.name || '').indexOf('chat') === 0 }" @click.prevent="$router.push({ name: 'chat' })">对话</a>
      <a v-if="isAdmin" :class="{ active: String($route.name || '').indexOf('admin-roles') === 0 }" @click.prevent="$router.push({ name: 'admin-roles' })">AI角色管理</a>
    </nav>
    <div class="right">
      <template v-if="isLoggedIn">
        <el-dropdown>
          <span class="user">
            <div class="avatar">👤</div>
            <span class="name">{{ user?.userName || user?.userAccount }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="$router.push({ name: 'chat' })">我的对话</el-dropdown-item>
              <el-dropdown-item v-if="isAdmin" @click="$router.push({ name: 'admin-roles' })">AI角色管理</el-dropdown-item>
              <el-dropdown-item divided @click="onLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
      <template v-else>
        <el-button size="small" plain @click="$router.push({ name: 'login', query: { redirect: $route.fullPath } })">登录</el-button>
        <el-button size="small" type="primary" @click="$router.push({ name: 'register' })">注册</el-button>
      </template>
    </div>
  </header>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const { user, isLoggedIn, isAdmin } = storeToRefs(userStore)

async function onLogout() { await userStore.logout() }

onMounted(() => { userStore.ensureInit() })
</script>

<style scoped>
.header { height: 60px; display: grid; grid-template-columns: 240px 1fr auto; align-items: center; gap: 16px; padding: 0 16px; border-bottom: 1px solid #eee; background: #fff; position: sticky; top: 0; z-index: 100; }
.left { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.logo { width: 28px; height: 28px; border-radius: 6px; background: #f3f5ff; display: flex; align-items: center; justify-content: center; font-size: 16px; }
.brand { font-weight: 800; color: #5a67d8; letter-spacing: 0.3px; }
.nav { display: flex; gap: 16px; }
.nav a { color: #333; text-decoration: none; font-weight: 500; cursor: pointer; }
.nav a.active { color: #5a67d8; }
.right { display: flex; align-items: center; gap: 8px; }
.user { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.avatar { width: 28px; height: 28px; border-radius: 50%; background: #f2f2f2; display: flex; align-items: center; justify-content: center; font-size: 14px; }
.name { color: #333; }
</style>
