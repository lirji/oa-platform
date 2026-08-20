import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 计划 §10 单测 7：**并发 401 只续期一次、只跳一次登录**。
 *
 * <p>★ 这条在 DEV 模式下永远不会触发（`devIdentity.renew()` 直接返回 false，
 * 后端也不会回 401）—— 计划里专门写了这件事：那样的话这条测试会一直
 * "绿但没验过真路径"，等切到 JWT 的那天才第一次真正运行。
 * 所以这里用一个**假 adapter** 直接驱动 `client.ts` 里的那段逻辑，
 * 与当前是 DEV 还是 JWT 无关。
 */
const fake = vi.hoisted(() => ({
  apply: vi.fn(async () => true),
  renew: vi.fn(async () => true),
  giveUp: vi.fn(async () => {}),
}))

vi.mock('./identity', () => ({
  currentIdentity: () => ({ name: 'FAKE', apply: fake.apply, renew: fake.renew, giveUp: fake.giveUp }),
}))

/** 造一个 401 的 AxiosError（拦截器读 error.response.status 与 error.config）。 */
function unauthorized(cfg: InternalAxiosRequestConfig): AxiosError {
  const resp = { status: 401, data: {}, statusText: 'Unauthorized', headers: {}, config: cfg } as AxiosResponse
  return new AxiosError('unauthorized', 'ERR_BAD_REQUEST', cfg, {}, resp)
}

async function freshClient(opts: { serverAcceptsAfterRenew: boolean }) {
  vi.resetModules()
  const { apiClient } = await import('./client')
  const state = { accepts: false, networkCalls: 0 }
  fake.renew.mockImplementation(async () => {
    if (opts.serverAcceptsAfterRenew) state.accepts = true
    return opts.serverAcceptsAfterRenew
  })
  apiClient.defaults.adapter = async (cfg) => {
    state.networkCalls++
    if (!state.accepts) throw unauthorized(cfg as InternalAxiosRequestConfig)
    return { status: 200, data: { ok: true }, statusText: 'OK', headers: {}, config: cfg } as AxiosResponse
  }
  return { apiClient, state }
}

beforeEach(() => {
  fake.apply.mockClear(); fake.renew.mockClear(); fake.giveUp.mockClear()
})

describe('401 续期单飞', () => {
  it('5 个并发 401 只续期一次，且全部重放成功', async () => {
    const { apiClient, state } = await freshClient({ serverAcceptsAfterRenew: true })
    const results = await Promise.all(
      Array.from({ length: 5 }, (_, i) => apiClient.get(`/api/v1/x/${i}`)),
    )
    expect(results.every((r) => r.status === 200)).toBe(true)
    expect(fake.renew).toHaveBeenCalledTimes(1)   // ← 惊群式续期
    expect(state.networkCalls).toBe(10)           // 5 次 401 + 5 次重放
    expect(fake.giveUp).not.toHaveBeenCalled()
  })

  it('★ 续期失败时也只跳一次登录', async () => {
    // giveUp 在 JWT 下是 signinRedirect —— 调 5 次就是连着导航 5 次。
    // "只触发一次续期"容易想到，"只跳一次登录"经常漏。
    const { apiClient } = await freshClient({ serverAcceptsAfterRenew: false })
    const results = await Promise.allSettled(
      Array.from({ length: 5 }, (_, i) => apiClient.get(`/api/v1/x/${i}`)),
    )
    expect(results.every((r) => r.status === 'rejected')).toBe(true)
    expect(fake.renew).toHaveBeenCalledTimes(1)
    expect(fake.giveUp).toHaveBeenCalledTimes(1)
  })

  it('重放后仍 401 不会无限重试', async () => {
    // renew 说续上了，但服务端照旧 401 —— `_retried` 守卫必须让它停在第二次。
    const { apiClient, state } = await freshClient({ serverAcceptsAfterRenew: false })
    fake.renew.mockImplementation(async () => true)
    await expect(apiClient.get('/api/v1/x')).rejects.toThrow()
    expect(state.networkCalls).toBe(2)
  })

  it('非 401 错误不触发续期', async () => {
    vi.resetModules()
    const { apiClient } = await import('./client')
    apiClient.defaults.adapter = async (cfg) => {
      const resp = { status: 403, data: {}, statusText: 'Forbidden', headers: {}, config: cfg } as AxiosResponse
      throw new AxiosError('forbidden', 'ERR', cfg as InternalAxiosRequestConfig, {}, resp)
    }
    await expect(apiClient.get('/api/v1/x')).rejects.toThrow()
    expect(fake.renew).not.toHaveBeenCalled()
  })
})
