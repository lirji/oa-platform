/** 后端 `GET /api/v1/me/permissions` 的返回。字段与 `MeController.MyPermissions` 一一对应。 */
export interface MyPermissions {
  userId: string | null
  username: string | null
  employeeId: number | null
  primaryOrgId: number | null
  primaryOrgPath: string | null
  /** `epoch * 1e6 + userVersion`，单调。前端据它判断"权限变了没有"。 */
  version: number
  permCodes: string[]
  menus: MenuNode[]
  /** 合并后的数据范围类型：ALL / ORG_AND_SUB / ORG / SELF / CUSTOM / NONE */
  dataScope: DataScopeType
  /** 数据范围的 org_path 前缀集合。空 + dataScope=NONE ⇒ 一行都看不到。 */
  scopePrefixes: string[]
  /** 我是谁的代理人。非空时界面要显示"你正代理 X"。 */
  delegators: string[]
  /** 当前处于活跃 JIT 提权状态的权限点。 */
  elevatedCodes: string[]
  /** 每模块独立的数据范围。 */
  moduleScope: Record<string, DataScopeType>
}

export type DataScopeType = 'ALL' | 'ORG_AND_SUB' | 'ORG' | 'SELF' | 'CUSTOM' | 'NONE'

export interface MenuNode {
  code: string
  name: string
  /** 后端 V13 已填。★ 前端不要再维护第二份菜单定义 —— 两份迟早漂移。 */
  icon: string | null
  route: string | null
  sortOrder: number
  children: MenuNode[]
}

/** 我当前生效中的临时提权（`GET /api/v1/iam/elevations/mine`）。 */
export interface Elevation {
  grantId: number
  roleId: number
  roleCode: string
  roleName: string
  grantedAt: string
  validTo: string
  /** 剩余毫秒。★ 用它做倒计时 —— `/explain` 的 expiresInMs 是快照缓存 TTL，不是这个。 */
  remainingMs: number
  reason: string | null
}

/** 数据范围的人话描述。空态提示要用它 —— 否则"没数据"与"没权限看"无法区分。 */
export const DATA_SCOPE_LABEL: Record<DataScopeType, string> = {
  ALL: '全部数据',
  ORG_AND_SUB: '本部门及下级',
  ORG: '仅本部门',
  SELF: '仅本人',
  CUSTOM: '自定义范围',
  NONE: '无可见范围',
}
