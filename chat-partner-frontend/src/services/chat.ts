import { httpPost } from './http'

export async function sendMessage(groupId: number, message: string) {
  return httpPost<string>('/api/chat/send', { groupId, message })
}

export async function initChat(groupId: number) {
  return httpPost<string>(`/api/chat/init?groupId=${groupId}`)
}
