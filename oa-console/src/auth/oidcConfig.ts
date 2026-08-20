import { UserManager, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts'
import { config } from '@oa/shared/config'

/**
 * Casdoor OIDC 配置（授权码 + PKCE）。
 *
 * <p>★ `authority` **直连 :8000，绝不走 vite proxy / nginx 反代** ——
 * 代理会改变 issuer，与 token 里的 `iss` 对不上，校验必失败。
 * 两个同族 console 的注释都写死了这条。
 */
export const oidcSettings: UserManagerSettings = {
  authority: config.casdoorAuthority,
  client_id: config.casdoorClientId,
  // 运行时算，不写死 —— 同一份构建产物在 5473(dev) 与 8404(prod) 上都能用
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: `${window.location.origin}/login`,
  response_type: 'code',
  scope: config.oidcScope,
  // OA 的权限来自后端 permission 表，不需要从 userinfo 端点补任何东西，
  // 省掉一次跨源请求（Casdoor 的 userinfo 需要额外配 CORS）
  loadUserInfo: false,
  automaticSilentRenew: true,
  // sessionStorage：关掉标签页即清。token 不持久化到磁盘是家族纪律。
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
}

/**
 * 命令式取 token 用的实例（axios 拦截器用）。
 *
 * <p>`automaticSilentRenew: false` —— 与 `<AuthProvider>` 里那个实例共用同一份自动续期
 * 会导致**双续期**：两个 UserManager 同时发现 token 快过期，各自发一次 silent renew。
 */
export const userManager = new UserManager({ ...oidcSettings, automaticSilentRenew: false })
