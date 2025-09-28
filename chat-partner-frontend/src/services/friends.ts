import { httpDelete, httpGet, httpPatch, httpPost } from './http'
import type { AiRole } from './roles'

export interface FriendRoleVO extends AiRole {
  roleId: number
  pinned?: number
  pinOrder?: number
  lastChatTime?: string
}

export async function listFriends() {
  return httpGet<FriendRoleVO[]>('/api/friend/list')
}

export async function addFriend(roleId: number) {
  // Backend expects @RequestParam Long roleId, so pass it via query string
  return httpPost<boolean>(`/api/friend/add?roleId=${roleId}`)
}

export async function removeFriend(roleId: number) {
  return httpDelete<boolean>(`/api/friend/${roleId}`)
}

export async function updatePin(roleId: number, pinned: boolean, pinOrder?: number) {
  const url = `/api/friend/${roleId}/pin?pinned=${pinned}${pinOrder != null ? `&pinOrder=${pinOrder}` : ''}`
  return httpPatch<boolean>(url)
}
