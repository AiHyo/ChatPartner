<template>
  <aside class="dock">
    <el-tooltip content="首页" placement="right"><div class="dock-item" @click="$router.push({ name: 'home' })">🏠</div></el-tooltip>
    <el-tooltip content="人物大全" placement="right"><div class="dock-item" @click="$router.push({ name: 'roles' })">🧩</div></el-tooltip>
    <el-tooltip content="对话" placement="right"><div class="dock-item" @click="$router.push({ name: 'chat' })">💬</div></el-tooltip>
  </aside>
</template>

<script setup lang="ts">
</script>

<style scoped>
.dock { width: 64px; border-right: 1px solid #eee; background: #fff; display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 8px 0; }
.dock-item { width: 40px; height: 40px; background: #f4f6fb; border-radius: 10px; display: flex; align-items: center; justify-content: center; cursor: pointer; }
.dock-item:hover { background: #eef2ff; }
</style>
