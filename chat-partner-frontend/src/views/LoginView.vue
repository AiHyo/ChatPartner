<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="hover">
      <h2 class="title">登录 ChatPartner</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="账号" prop="userAccount">
          <el-input v-model="form.userAccount" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="userPassword">
          <el-input v-model="form.userPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="onSubmit" style="width: 100%">登录</el-button>
        </el-form-item>
      </el-form>
      <div class="tips">没有账号？<a @click.prevent="$router.push({ name: 'register', query: { redirect: $route.query.redirect } })">去注册</a></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ userAccount: '', userPassword: '' })

const rules = {
  userAccount: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  userPassword: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  // @ts-ignore
  await formRef.value?.validate()
  loading.value = true
  try {
    await userStore.login(form.userAccount, form.userPassword)
    const redirectRaw = (route.query.redirect as string) || '/'
    const redirect = redirectRaw ? decodeURIComponent(redirectRaw) : '/'
    ElMessage.success('登录成功')
    router.replace(redirect)
  } catch (error: any) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { display: flex; justify-content: center; padding: 48px 16px; }
.auth-card { width: 420px; }
.title { text-align: center; margin: 0 0 16px; }
.tips { text-align: center; color: #666; }
.tips a { color: #5a67d8; cursor: pointer; }
</style>
