import type { InternalAxiosRequestConfig } from 'axios'
import { config, devUserOverride } from '../config'
import { userManager } from '../../auth/oidcConfig'

/**
 * 身份注入的**可切换 adapter**。
 *
 * <p>★ 为什么要抽这一层：项目分两个阶段 —— 先在 DEV 模式用 `X-OA-User` 头联调、
 * 后切 JWT 用 Casdoor token。如果两种注入散在拦截器的 if/else 里，
 * 切换时**每一条请求路径都要重新验证一遍**，而 401 续期单飞这类逻辑
 * 在 DEV 下根本不会触发 —— 它的单测会一直"绿但没验过真路径"。
 *
 * <p>抽成 adapter 后，切换只换一个实现，其余代码（含 e2e 的身份切换）不动。
 */
export interface IdentityAdapter {
  readonly name: string
  /** 往请求上装配身份。返回 false 表示"没有身份"，调用方可据此提前短路。 */
  apply(cfg: InternalAxiosRequestConfig): Promise<boolean>
  /** 401 后尝试续期。返回 true 表示已续上、可重试原请求。 */
  renew(): Promise<boolean>
  /** 续期也失败时的收尾（跳登录 / 清状态）。 */
  giveUp(): Promise<void>
}

/**
 * DEV：把 `X-OA-User` 塞进请求头。
 *
 * <p>后端 `UserContextFilter` 的逻辑是 **JWT 优先、否则回退这个头** ——
 * 所以它在 JWT 模式下对已认证请求无效，不会造成"两种身份打架"。
 */
export function devIdentity(getUser: () => string): IdentityAdapter {
  return {
    name: 'DEV(X-OA-User)',
    async apply(cfg) {
      // ★ 调用方显式传了身份就不要覆盖 —— 权限沙盘要"以他人身份"查可见行数，
      //   拦截器无脑覆盖会让它量到【当前登录者】的数字。
      //   那个数字看起来完全合理（就是一个人数），却是错的，
      //   而这一页存在的全部意义就是解释"这个人到底能看到什么"。
      if (cfg.headers['X-OA-User']) return true
      const u = getUser()
      if (!u) return false
      cfg.headers['X-OA-User'] = u
      return true
    },
    // DEV 没有 token，也就没有续期这回事。返回 false 让调用方把错误透传给业务处理。
    async renew() { return false },
    async giveUp() { /* DEV 下不跳登录：跳了也没有登录页可去 */ },
  }
}

/** JWT：Casdoor token。续期走 oidc 的 signinSilent，单飞由调用方保证。 */
export function jwtIdentity(deps: {
  getToken: () => Promise<string | null>
  silentRenew: () => Promise<string | null>
  redirectToLogin: () => Promise<void>
}): IdentityAdapter {
  return {
    name: 'JWT(Casdoor)',
    async apply(cfg) {
      const t = await deps.getToken()
      if (!t) return false
      cfg.headers.Authorization = `Bearer ${t}`
      return true
    },
    async renew() {
      const t = await deps.silentRenew()
      return Boolean(t)
    },
    async giveUp() { await deps.redirectToLogin() },
  }
}

/**
 * 当前生效的 adapter。切 JWT 时只改 `VITE_AUTH_ENABLED`，代码不动。
 *
 * <p>用静态 import 而不是动态 require：Vite/ESM 下 `require` 根本不存在，
 * 而动态 `import()` 会让这个函数变成异步、污染所有调用方。
 * oidc-client-ts 已经在 `manualChunks` 里单独分块，DEV 模式下它只是被下载、不会被执行。
 */
export function currentIdentity(): IdentityAdapter {
  // 运行期覆写优先（e2e 切身份用），JWT 模式下 devUserOverride() 恒为 null
  if (!config.authEnabled) return devIdentity(() => devUserOverride() ?? config.devUser)
  return jwtIdentity({
    getToken: async () => {
      const u = await userManager.getUser()
      return u && !u.expired ? u.access_token : null
    },
    silentRenew: async () => (await userManager.signinSilent())?.access_token ?? null,
    redirectToLogin: async () => {
      await userManager.signinRedirect({ state: { returnTo: window.location.pathname } })
    },
  })
}
