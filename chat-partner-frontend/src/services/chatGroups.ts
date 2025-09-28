import { httpGet, httpPost } from './http'

export interface ChatGroup {
  id: number
  groupName: string
  roleId: number
  userId: number
  lastChatTime?: string
}

export async function getGroupsByRole(roleId: number) {
  return httpGet<ChatGroup[]>('/api/chatGroup/byRole', { roleId })
}

export async function getLatestGroupByRole(roleId: number) {
  return httpGet<ChatGroup | null>('/api/chatGroup/latestByRole', { roleId })
}

export async function getMyGroups() {
  return httpGet<ChatGroup[]>('/api/chatGroup/my')
}

export async function createGroup(roleId: number, groupName?: string) {
  return httpPost<number>('/api/chatGroup/add', { roleId, groupName: groupName || '新话题' })
}

export async function updateGroup(body: { id: number; groupName?: string; roleId?: number }) {
  return httpPost<boolean>('/api/chatGroup/update', body)
}

export async function deleteGroup(id: number) {
  return httpPost<boolean>('/api/chatGroup/delete', { id })
}
