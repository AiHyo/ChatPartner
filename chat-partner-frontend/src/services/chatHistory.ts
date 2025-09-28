import { httpGet } from './http'

export interface ChatHistoryItem {
  id: number
  groupId: number
  userId: number
  message: string
  messageType: 'user' | 'ai'
  createTime: string
}

export interface CursorPageResp {
  items: ChatHistoryItem[]
  nextCursor?: string | null
  hasMore: boolean
}

export async function getHistoryByCursor(groupId: number, cursor?: string, limit = 10, asc = true) {
  return httpGet<CursorPageResp>('/api/chatHistory/cursor', { groupId, cursor, limit, asc })
}

export async function getLatest(groupId: number, limit = 10) {
  // backend default latest returns 10; we keep param for future
  return httpGet<ChatHistoryItem[]>('/api/chatHistory/latest', { groupId })
}
