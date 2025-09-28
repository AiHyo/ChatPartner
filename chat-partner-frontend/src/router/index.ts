import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  { path: '/roles', name: 'roles', component: () => import('../views/RolesView.vue') },
  { path: '/chat/:roleId?/:groupId?', name: 'chat', component: () => import('../views/ChatView.vue'), props: true, meta: { requiresAuth: true } },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue') },
  { path: '/admin/roles', name: 'admin-roles', component: () => import('../views/admin/AdminRolesView.vue'), meta: { requiresAuth: true, adminOnly: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() { return { top: 0 } },
})

router.beforeEach(async (to: any) => {
  const store = useUserStore()
  await store.ensureInit()
  
  console.log('Router guard:', {
    to: to.path,
    requiresAuth: to.meta?.requiresAuth,
    adminOnly: to.meta?.adminOnly,
    user: store.user,
    isLoggedIn: store.isLoggedIn,
    isAdmin: store.isAdmin
  })
  
  const isLoginPage = to.name === 'login' || to.path.startsWith('/login')
  if (to.meta?.requiresAuth && !store.isLoggedIn) {
    if (!isLoginPage) {
      console.log('Redirecting to login, not authenticated')
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  if (to.meta?.adminOnly && !store.isAdmin) {
    console.log('Redirecting to home, not admin')
    return { name: 'home' }
  }
})

export default router
