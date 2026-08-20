import { create } from 'zustand'

/**
 * 认证会话镜像（**不含授权**）。
 *
 * <p>★ 与 workflow-console 的同名文件最大的差别：这里**没有 `canRead` / `isAdmin`**。
 * 那两个函数在原版里是按 Casdoor group 名（`PHARMACIST` / `ADMIN`）硬编码判定的 ——
 * 直接照抄过来会让 OA 的每个人都 403，因为 OA 里根本没有那两个组。
 * FINAL_PLAN §8.2 把这条列为"克隆 console 时的已知坑"。
 *
 * <p>OA 的授权判定**一律走 `usePerm()`**（数据源是后端的 `permCodes`），
 * 不解 JWT 的 groups claim —— 万级权限码也塞不进 token。
 * 这个 store 只回答"你是谁"，不回答"你能做什么"。
 */
export type AuthStatus = 'loading' | 'authed' | 'anonymous'

interface AuthState {
  status: AuthStatus
  userId: string | null
  username: string | null
  set: (s: Partial<Omit<AuthState, 'set' | 'clear'>>) => void
  clear: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'loading',
  userId: null,
  username: null,
  set: (s) => set(s),
  clear: () => set({ status: 'anonymous', userId: null, username: null }),
}))
