<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="hover">
      <h2 class="title">注册 ChatPartner</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="账号" prop="userAccount">
          <el-input v-model="form.userAccount" />
        </el-form-item>
        <el-form-item label="密码" prop="userPassword">
          <el-input v-model="form.userPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="checkPassword">
          <el-input v-model="form.checkPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="onSubmit" style="width: 100%">注册</el-button>
        </el-form-item>
      </el-form>
      <div class="tips">已有账号？<a @click.prevent="$router.push({ name: 'login', query: { redirect: $route.query.redirect } })">去登录</a></div>
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
const form = reactive({ userAccount: '', userPassword: '', checkPassword: '' })

const rules = {
  userAccount: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  userPassword: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  checkPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: (_: any, v: string, cb: any) => v === form.userPassword ? cb() : cb(new Error('两次密码不一致')), trigger: 'blur' },
  ],
}

async function onSubmit() {
  // @ts-ignore
  await formRef.value?.validate()
  loading.value = true
  try {
    await userStore.register(form.userAccount, form.userPassword, form.checkPassword)
    ElMessage.success('注册成功，请登录')
    const redirect = (route.query.redirect as string) || '/'
    router.replace({ name: 'login', query: { redirect } })
  } catch (error: any) {
    ElMessage.error(error.message || '注册失败')
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
