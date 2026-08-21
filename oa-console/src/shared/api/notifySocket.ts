import { config } from '../config'
import { apiClient } from './client'

interface WsTicketResponse {
  data: { ticket: string; expiresInSeconds: number }
}

/** 把同源 HTTP 路径安全地转换成 WS URL；参数中从不接受 access token。 */
export function websocketUrl(path: string, origin: string, ticket: string): string {
  const url = new URL(path, origin)
  if (url.protocol === 'http:') url.protocol = 'ws:'
  else if (url.protocol === 'https:') url.protocol = 'wss:'
  else if (url.protocol !== 'ws:' && url.protocol !== 'wss:') throw new Error('不支持的 WebSocket 协议')
  url.search = ''
  url.searchParams.set('ticket', ticket)
  return url.toString()
}

/** 先通过带 Bearer 的 REST 换一次性 ticket，再建立浏览器原生 WebSocket。 */
export async function openNotifySocket(): Promise<WebSocket> {
  const response = await apiClient.post<WsTicketResponse>('/api/v1/notify/ws-ticket')
  const ticket = response.data.data.ticket
  return new WebSocket(websocketUrl(config.wsPath, window.location.origin, ticket))
}

/** 兼容后端事件名大小写；其它通知消息由业务通知 UI 消费，不触发权限重拉。 */
export function socketPermVersion(payload: string): number | null {
  try {
    const event = JSON.parse(payload) as { type?: unknown; version?: unknown }
    if (event.type !== 'perm.changed' && event.type !== 'PERM_CHANGED') return null
    const version = Number(event.version)
    return Number.isSafeInteger(version) && version >= 0 ? version : null
  } catch {
    return null
  }
}
