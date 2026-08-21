import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { config, currentDevUser } from '../config'
import { userManager } from '../auth/oidc'

export const api = axios.create({ baseURL: config.apiBaseUrl, timeout: 15_000 })
api.interceptors.request.use(async (request) => {
  if (!config.authEnabled) request.headers['X-OA-User'] = currentDevUser()
  else {
    const user = await userManager.getUser()
    if (user && !user.expired) request.headers.Authorization = `Bearer ${user.access_token}`
  }
  return request
})

let renewing: Promise<boolean> | null = null
api.interceptors.response.use((response) => response, async (error: AxiosError) => {
  const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
  if (!config.authEnabled || error.response?.status !== 401 || !original || original._retried) throw error
  original._retried = true
  renewing ??= userManager.signinSilent().then(Boolean).catch(() => false).finally(() => { renewing = null })
  if (await renewing) return api(original)
  await userManager.signinRedirect({ state: { returnTo: window.location.pathname } })
  throw error
})

export function errorText(error: unknown): string {
  const e = error as AxiosError<{ message?: string }>
  if (!e.response) return '网络不可达，请稍后重试'
  if (e.response.status >= 500) return '服务暂时不可用，请稍后重试'
  return e.response.data?.message ?? '操作失败'
}
