<template>
  <footer class="footer">
    <div class="container">
      <span>© {{ new Date().getFullYear() }} ChatPartner</span>
      <span class="dot">•</span>
      <span>实时语音对话 · LLM + 七牛 ASR/TTS</span>
    </div>
  </footer>
</template>

<script setup lang="ts">
</script>

<style scoped>
.footer { height: 48px; border-top: 1px solid #eee; background: #fff; display: flex; align-items: center; }
.container { max-width: 1200px; margin: 0 auto; padding: 0 16px; width: 100%; color: #666; display: flex; align-items: center; gap: 8px; }
.dot { color: #ccc; }
</style>
