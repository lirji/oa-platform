import { create } from 'zustand'
import type { DataScopeType, Elevation, MenuNode, MyPermissions } from '@oa/shared/perm/types'
import { toPermSet, type PermSet } from '@oa/shared/perm/evaluate'

/**
 * 权限镜像。
 *
 * <p><b>权威源是 react-query</b>（`usePermissionsQuery`），这里只是**供同步读取的镜像** ——
 * 与家族里 `AuthBridge` 把 oidc 会话同步进 authStore 是同一个模式。
 *
 * <p>为什么需要镜像而不是直接用 query：
 * ① `permCodes` 是数组，一个页面判几十次权限，`includes()` 是 O(n)，这里存 Set；
 * ② axios 拦截器、路由守卫这些**非 React 的地方**要能同步读到（`getState()`）；
 * ③ version 去重逻辑需要一个有状态的落点。
 */
interface PermState {
  loaded: boolean
  /** 未认证。★ 与"加载中"和"已认证但没权限"是三件不同的事。 */
  anonymous: boolean
  userId: string | null
  username: string | null
  employeeId: number | null
  primaryOrgId: number | null
  primaryOrgPath: string | null
  version: number
  codes: PermSet
  menus: MenuNode[]
  dataScope: DataScopeType
  scopePrefixes: string[]
  moduleScope: Record<string, DataScopeType>
  /** 我是谁的代理人。非空时顶栏要显示"你正代理 X 的待办"。 */
  delegators: string[]
  elevated: PermSet
  /** 活跃的临时提权（含到期时间）。由 useElevations 写入。 */
  elevations: Elevation[]

  applyPermissions: (p: MyPermissions) => void
  setAnonymous: () => void
  setElevations: (list: Elevation[]) => void
  reset: () => void
}

const EMPTY: Omit<PermState, 'applyPermissions' | 'setAnonymous' | 'setElevations' | 'reset'> = {
  loaded: false,
  anonymous: false,
  userId: null,
  username: null,
  employeeId: null,
  primaryOrgId: null,
  primaryOrgPath: null,
  version: 0,
  codes: new Set(),
  menus: [],
  dataScope: 'NONE',
  scopePrefixes: [],
  moduleScope: {},
  delegators: [],
  elevated: new Set(),
  elevations: [],
}

export const usePermStore = create<PermState>((set, get) => ({
  ...EMPTY,

  applyPermissions: (p) => {
    // ★ version 去重：一次撤权会 bump 全局 epoch，于是【全公司每个人】的 version 都变。
    //   不去重的话，任何一次他人撤权都会让本地整棵菜单重渲染一遍。
    if (get().loaded && get().version === p.version) return
    set({
      loaded: true,
      anonymous: false,
      userId: p.userId,
      username: p.username,
      employeeId: p.employeeId,
      primaryOrgId: p.primaryOrgId,
      primaryOrgPath: p.primaryOrgPath,
      version: p.version,
      codes: toPermSet(p.permCodes),
      menus: p.menus ?? [],
      dataScope: p.dataScope,
      scopePrefixes: p.scopePrefixes ?? [],
      moduleScope: p.moduleScope ?? {},
      delegators: p.delegators ?? [],
      elevated: toPermSet(p.elevatedCodes ?? []),
    })
  },

  setAnonymous: () => set({ ...EMPTY, loaded: true, anonymous: true }),
  setElevations: (list) => set({ elevations: list }),
  reset: () => set({ ...EMPTY }),
}))
