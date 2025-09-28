import { httpGet, httpPost } from './http'

export interface LoginUserVO {
  id: number
  userAccount: string
  userName?: string
  userAvatar?: string
  userProfile?: string
  userRole: 'user' | 'admin'
  createTime?: string
  updateTime?: string
}

export async function getLoginUser() {
  return httpGet<LoginUserVO>('/api/user/get/login')
}

export async function login(body: { userAccount: string; userPassword: string }) {
  return httpPost<LoginUserVO>('/api/user/login', body)
}

export async function register(body: { userAccount: string; userPassword: string; checkPassword: string }) {
  return httpPost<number>('/api/user/register', body)
}

export async function logout() {
  return httpPost<boolean>('/api/user/logout')
}
