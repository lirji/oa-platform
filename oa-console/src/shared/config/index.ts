/**
 * 环境变量的**唯一**读取出口。
 *
 * <p>全项目只有这一个文件碰 `import.meta.env` —— 这是家族约定：
 * 散在各处读环境变量，改一个键名要全局搜，而漏掉的那处会在运行期才炸。
 */
const trimSlash = (s: string) => s.replace(/\/+$/, '')

export const config = {
  /** 留空 = 相对路径：dev 走 vite proxy、prod 走 nginx 同源反代，两边都不产生跨源请求。 */
  apiBaseUrl: trimSlash(import.meta.env.VITE_API_BASE_URL ?? ''),

  /**
   * 鉴权开关。false 时前端不走 Casdoor，直接用 devUser 注入 `X-OA-User`。
   * 与后端的 `oa.security.mode=DEV` 配对使用。
   */
  authEnabled: (import.meta.env.VITE_AUTH_ENABLED ?? 'false') === 'true',

  /**
   * DEV 模式下冒充的身份。
   * ★ 它与 JWT 的 token 注入必须是**同一个可切换 adapter**（见 shared/api/client.ts），
   * 否则切 JWT 时所有请求路径都要改一遍。
   */
  devUser: import.meta.env.VITE_DEV_USER ?? 'seed-user-1',

  casdoorAuthority: trimSlash(import.meta.env.VITE_CASDOOR_AUTHORITY ?? 'http://localhost:8000'),
  casdoorClientId: import.meta.env.VITE_CASDOOR_CLIENT_ID ?? '',
  oidcScope: import.meta.env.VITE_OIDC_SCOPE ?? 'openid profile email',

  /** 长连地址。dev 走 vite proxy 的 /ws，prod 走 nginx。 */
  wsPath: import.meta.env.VITE_WS_PATH ?? '/ws',
} as const
