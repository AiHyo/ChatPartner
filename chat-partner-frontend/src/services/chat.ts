import { httpPost } from './http'

export async function sendMessage(groupId: number, message: string) {
  return httpPost<string>('/api/chat/send', { groupId, message })
}

export async function initChat(groupId: number) {
  return httpPost<string>(`/api/chat/init?groupId=${groupId}`)
}

// SSE streaming: returns a cancel function
export function sendMessageStreamSse(
  groupId: number,
  message: string,
  onChunk: (token: string) => void,
  onDone: () => void,
  onError?: (e: any) => void,
) {
  const url = `/api/chat/stream?groupId=${groupId}&message=${encodeURIComponent(message)}`
  // @ts-ignore withCredentials may not be typed in TS lib
  const es: EventSource = new EventSource(url, { withCredentials: true })
  es.onmessage = (e: MessageEvent<string>) => {
    try {
      const obj = JSON.parse(e.data)
      onChunk(obj?.data ?? e.data)
    } catch {
      onChunk(e.data)
    }
  }
  es.addEventListener('done', () => {
    try { es.close() } catch {}
    onDone()
  })
  es.onerror = (e) => {
    try { es.close() } catch {}
    onError?.(e)
  }
  return () => { try { es.close() } catch {} }
}
