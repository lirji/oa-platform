import { useEffect } from 'react'
import { useAuth } from 'react-oidc-context'
import { config } from '@oa/shared/config'
import { useAuthStore } from '../store/authStore'

/**
 * 把 OIDC 会话同步进 authStore 镜像（供非 React 处同步读取）。
 *
 * <p>★ 与 workflow-console 的原版差一处：**不再从 access_token 里解 groups**。
 * OA 的授权走后端 `permCodes`（见 `PermBridge`），groups claim 在这里没有用武之地 ——
 * 照抄那段代码会引入一个"看起来在用、其实没人读"的字段，
 * 而将来有人真去读它，就会得到一套与后端不一致的判定。
 */
export default function AuthBridge() {
  const auth = useAuth()
  const set = useAuthStore((s) => s.set)
  const clear = useAuthStore((s) => s.clear)

  useEffect(() => {
    // DEV 模式不走 OIDC：直接把配置里的身份写进镜像，让界面能正常显示"当前是谁"。
    if (!config.authEnabled) {
      set({ status: 'authed', userId: config.devUser, username: config.devUser })
      return
    }
    if (auth.isLoading) return
    if (auth.isAuthenticated && auth.user) {
      const p = auth.user.profile
      set({
        status: 'authed',
        userId: p.sub,
        username: (p.preferred_username as string | undefined) ?? (p.name as string | undefined) ?? p.sub,
      })
    } else {
      clear()
    }
  }, [auth.isLoading, auth.isAuthenticated, auth.user, set, clear])

  return null
}
