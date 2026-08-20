import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { config } from '../config'
import { currentIdentity } from './identity'

/**
 * 唯一的 HTTP 客户端。四个后端（8400/8401/8402/8403）共用它 ——
 * 各自建 client 的话，401 续期、错误映射、权限版本比对这些逻辑要写四遍，
 * 而漏掉的那一份会在某个模块上安静地失效。
 *
 * <p>baseURL 留空 → 相对路径 → dev 走 vite proxy、prod 走 nginx 同源反代。
 * **不跨源**：后端虽然配了 CORS 兜底，但同源反代才是主路径。
 */
export const apiClient = axios.create({
  baseURL: config.apiBaseUrl,
  timeout: 15000,
})

/** 权限版本变化的订阅者。由 PermBridge 注册。 */
type PermVersionListener = (version: number) => void
let permVersionListener: PermVersionListener | null = null
export function onPermVersionChanged(fn: PermVersionListener) {
  permVersionListener = fn
}

const identity = currentIdentity()

apiClient.interceptors.request.use(async (cfg) => {
  await identity.apply(cfg)
  return cfg
})

/**
 * 401 单飞续期：多个请求同时 401 时共享同一个 in-flight promise，
 * 防止惊群式地发起 N 次续期、以及跳 N 次登录。
 */
let renewing: Promise<boolean> | null = null

/**
 * ★ **放弃也要单飞**。原来只对 renew 做了单飞，giveUp 是每个失败请求各调一次 ——
 * 5 个并发 401 就是 5 次 `signinRedirect()`，连着导航五次。
 * "只续期一次"容易想到，"只跳一次登录"经常漏，而它同样会毁掉登录流程
 * （最后一次导航可能覆盖掉前面已经带上的 state，回跳到错的地方）。
 */
let givingUp: Promise<void> | null = null

apiClient.interceptors.response.use(
  (resp) => {
    readPermVersion(resp.headers)
    return resp
  },
  async (error: AxiosError) => {
    // ★ 403 上也带权限版本头 —— 这是区分"你的权限刚被改了"与"你本来就没有"的关键。
    if (error.response) readPermVersion(error.response.headers)

    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    if (error.response?.status !== 401 || !original || original._retried) {
      return Promise.reject(error)
    }
    original._retried = true
    try {
      if (!renewing) {
        renewing = identity.renew().finally(() => { renewing = null })
      }
      if (await renewing) return apiClient(original)
    } catch {
      // 续期本身失败，落到 giveUp
    }
    if (!givingUp) {
      givingUp = identity.giveUp().finally(() => { givingUp = null })
    }
    await givingUp
    return Promise.reject(error)
  },
)

function readPermVersion(headers: unknown) {
  const h = headers as Record<string, string | undefined> | undefined
  const raw = h?.['x-oa-perm-version']
  if (!raw) return
  const v = Number(raw)
  if (Number.isFinite(v) && permVersionListener) permVersionListener(v)
}

/** 当前身份 adapter 的名字。冒烟与调试面板用它确认"现在是哪种模式"。 */
export const identityName = identity.name
