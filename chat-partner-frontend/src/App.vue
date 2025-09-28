<script setup lang="ts">
import HeaderBar from '@/components/layout/HeaderBar.vue'
import SideDock from '@/components/layout/SideDock.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
</script>

<template>
  <div class="app">
    <HeaderBar />
    <div class="content">
      <SideDock />
      <main class="main">
        <div class="container">
          <router-view />
        </div>
      </main>
    </div>
    <SiteFooter />
  </div>
</template>

<style scoped>
.app { min-height: 100vh; display: grid; grid-template-rows: auto 1fr auto; }
.content { display: grid; grid-template-columns: 64px 1fr; }
.main { background: #f7f9fc; }
.container { max-width: 1200px; margin: 0 auto; padding: 16px; }
</style>
