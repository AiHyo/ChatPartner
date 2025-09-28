import { httpGet, httpPost } from './http'

export interface AiRole {
  id: number
  roleName: string
  roleDescription?: string
  avatar?: string
  tags?: string
  likes?: number
  greeting?: string
  systemPrompt?: string
  isSystem?: number
  isActive?: number
  createTime?: string
  updateTime?: string
}

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export async function searchRoles(params: {
  q?: string
  tag?: string
  sort?: 'hot' | 'new' | 'name'
  page?: number
  size?: number
  onlyNotFriend?: boolean
}) {
  return httpGet<AiRole[]>('/api/aiRole/roles', params)
}

export async function searchRolesPage(params: {
  q?: string
  tag?: string
  sort?: 'hot' | 'new' | 'name'
  page?: number
  size?: number
  onlyNotFriend?: boolean
}) {
  return httpGet<PageResponse<AiRole>>('/api/aiRole/roles/page', params)
}

export async function listTags() {
  return httpGet<string[]>('/api/aiRole/tags')
}

export async function likeRole(roleId: number) {
  return httpPost<boolean>(`/api/aiRole/${roleId}/like`)
}

export async function toggleLike(roleId: number) {
  return httpPost<boolean>(`/api/aiRole/${roleId}/toggle-like`)
}

export async function getLikedRoleIds() {
  return httpGet<number[]>(`/api/aiRole/liked-ids`)
}
