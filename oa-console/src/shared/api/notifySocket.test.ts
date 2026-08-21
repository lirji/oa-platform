import { describe, expect, it } from 'vitest'
import { socketPermVersion, websocketUrl } from './notifySocket'

describe('WebSocket 一次性 ticket', () => {
  it('只把 ticket 放进 URL，不保留已有查询串或长期 token', () => {
    const url = websocketUrl('/ws?access_token=must-not-leak', 'https://oa.example.com/workbench', 'abc_123')
    expect(url).toBe('wss://oa.example.com/ws?ticket=abc_123')
    expect(url).not.toContain('access_token')
  })

  it('本地 http 使用 ws', () => {
    expect(websocketUrl('/ws', 'http://127.0.0.1:5473', 't')).toBe('ws://127.0.0.1:5473/ws?ticket=t')
  })

  it('只解析权限变更事件的安全整数版本', () => {
    expect(socketPermVersion('{"type":"perm.changed","version":42}')).toBe(42)
    expect(socketPermVersion('{"type":"NOTIFICATION","version":42}')).toBeNull()
    expect(socketPermVersion('not-json')).toBeNull()
  })
})
