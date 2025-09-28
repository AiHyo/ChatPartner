import { httpGet, httpPost, httpPatch, httpDelete } from './http'
import type { PageResponse } from './roles'
import type { AiRole } from './roles'

export interface AdminRoleUpsertRequest {
  id?: number
  roleName: string
  roleDescription?: string
  greeting?: string
  systemPrompt?: string
  avatar?: string
  tags?: string
  isSystem?: number // 0/1
  isActive?: number // 0/1
}

export async function adminPage(params: {
  q?: string
  tag?: string
  sort?: 'hot' | 'new' | 'name'
  page?: number
  size?: number
  isActive?: number
  isSystem?: number
}) {
  return httpGet<PageResponse<AiRole>>('/api/admin/aiRole/page', params)
}

export async function adminCreate(body: AdminRoleUpsertRequest) {
  return httpPost<number>('/api/admin/aiRole/create', body)
}

export async function adminUpdate(body: AdminRoleUpsertRequest) {
  return httpPatch<boolean>('/api/admin/aiRole/update', body)
}

export async function adminChangeStatus(id: number, isActive: number) {
  return httpPatch<boolean>(`/api/admin/aiRole/${id}/status?isActive=${isActive}`)
}

export async function adminDelete(id: number) {
  return httpDelete<boolean>(`/api/admin/aiRole/${id}`)
}

export async function adminDeleteBatch(ids: number[]) {
  return httpPost<boolean>('/api/admin/aiRole/deleteBatch', { ids })
}
