import { useAuthStore } from '@/stores/auth'

export interface SseMessage {
  event: string
  data: string
  id?: string
}

export interface SseCallbacks {
  onMessage: (message: SseMessage) => void
  onError?: (message: string) => void
}

export function subscribeSse(url: string, callbacks: SseCallbacks): () => void {
  const controller = new AbortController()
  const auth = useAuthStore()
  const headers: Record<string, string> = {}
  if (auth.token) {
    headers.Authorization = `Bearer ${auth.token}`
  }

  ;(async () => {
    let response: Response
    try {
      response = await fetch(url, {
        method: 'GET',
        headers,
        signal: controller.signal,
      })
    } catch (err: any) {
      if (err.name !== 'AbortError') callbacks.onError?.(err.message ?? '事件流连接失败')
      return
    }

    if (!response.ok) {
      try {
        const data = await response.json()
        callbacks.onError?.(data.message ?? `HTTP ${response.status}`)
      } catch {
        callbacks.onError?.(`HTTP ${response.status}`)
      }
      return
    }

    const reader = response.body?.getReader()
    if (!reader) {
      callbacks.onError?.('事件流响应为空')
      return
    }

    const decoder = new TextDecoder()
    let buffer = ''
    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const parts = buffer.split('\n\n')
        buffer = parts.pop() ?? ''
        for (const part of parts) {
          const message = parseSseMessage(part)
          if (message) callbacks.onMessage(message)
        }
      }
    } catch (err: any) {
      if (err.name !== 'AbortError') callbacks.onError?.(err.message ?? '事件流连接中断')
    }
  })()

  return () => controller.abort()
}

function parseSseMessage(raw: string): SseMessage | null {
  const dataLines: string[] = []
  let event = 'message'
  let id: string | undefined
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    } else if (line.startsWith('id:')) {
      id = line.slice(3).trim()
    }
  }
  if (dataLines.length === 0) {
    return null
  }
  return { event, data: dataLines.join('\n'), id }
}
