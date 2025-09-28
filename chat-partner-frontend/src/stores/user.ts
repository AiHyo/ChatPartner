import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getLoginUser, login as apiLogin, logout as apiLogout, register as apiRegister } from '@/services/user'
import type { LoginUserVO } from '@/services/user'

export const useUserStore = defineStore('user', () => {
  const user = ref<LoginUserVO | null>(null)
  const inited = ref(false)

  const isLoggedIn = computed(() => !!user.value?.id)
  const isAdmin = computed(() => user.value?.userRole === 'admin')

  async function ensureInit() {
    if (inited.value) return
    try {
      user.value = await getLoginUser()
    } catch (_) {
      user.value = null
    } finally {
      inited.value = true
    }
  }

  async function login(userAccount: string, userPassword: string) {
    const u = await apiLogin({ userAccount, userPassword })
    user.value = u
    inited.value = true
    return u
  }

  async function register(userAccount: string, userPassword: string, checkPassword: string) {
    return apiRegister({ userAccount, userPassword, checkPassword })
  }

  async function logout() {
    try { await apiLogout() } catch (_) {}
    user.value = null
  }

  return { user, inited, isLoggedIn, isAdmin, ensureInit, login, register, logout }
})
