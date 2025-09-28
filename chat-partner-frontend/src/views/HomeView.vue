<template>
  <div class="home">
    <section class="hero">
      <div class="container">
        <h1>ChatPartner</h1>
        <p class="sub">一个以「与角色对话聊天」为核心、支持「实时语音对话」的可爱明亮风站点</p>
        <div class="cta">
          <el-button type="primary" size="large" @click="$router.push('/chat')">Start 对话</el-button>
          <el-button size="large" @click="$router.push('/roles')">发现角色</el-button>
          <el-button size="large" plain @click="$router.push('/login')">登录</el-button>
          <el-button size="large" type="success" plain @click="$router.push('/register')">注册</el-button>
        </div>
      </div>
    </section>
    <section class="features container">
      <el-row :gutter="16">
        <el-col :span="8"><div class="feature-card">💬 多分组对话<br/><span>按角色管理会话话题，游标加载历史</span></div></el-col>
        <el-col :span="8"><div class="feature-card">🎙 实时语音<br/><span>ASR partial → LLM token → TTS 流式</span></div></el-col>
        <el-col :span="8"><div class="feature-card">🧩 人物大全<br/><span>标签、热门排序、点赞、添加好友</span></div></el-col>
      </el-row>
    </section>
  </div>
  
</template>

<script setup lang="ts">
</script>

<style scoped>
.container { max-width: 1200px; margin: 0 auto; padding: 0 16px; }
.home { display: flex; flex-direction: column; gap: 24px; }
.hero { background: linear-gradient(180deg, #f6f8ff, #ffffff); padding: 64px 0; }
.hero h1 { font-size: 54px; margin: 0 0 8px; text-align: center; color: #4c51bf; font-weight: 900; }
.hero .sub { color: #667085; margin: 8px auto 24px; text-align: center; max-width: 760px; }
.cta { display: flex; gap: 12px; justify-content: center; }
.features { padding-bottom: 24px; }
.feature-card { background: #fff; border: 1px solid #eee; border-radius: 12px; padding: 16px; text-align: center; font-weight: 700; }
.feature-card span { display: block; color: #667085; font-weight: 400; margin-top: 6px; }
</style>
