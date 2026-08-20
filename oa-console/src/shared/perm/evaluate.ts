import type { DataScopeType, MenuNode, MyPermissions } from './types'

/**
 * 判权的**纯函数核**（不含 React）。
 *
 * <p>放在 `shared/` 且不 import 任何框架：Phase 6 的 antd-mobile H5 直接复用这一层，
 * 只有渲染层各写一份。这也是 `src/shared/**` 那条"不许 import antd/react-router"
 * 纪律的具体兑现。
 */

/** 权限集合。用 Set 而不是数组：一个页面判几十次，`includes()` 是 O(n)。 */
export type PermSet = ReadonlySet<string>

export function toPermSet(codes: readonly string[]): PermSet {
  return new Set(codes)
}

export function has(set: PermSet, code: string): boolean {
  return set.has(code)
}

export function hasAny(set: PermSet, codes: readonly string[]): boolean {
  return codes.some((c) => set.has(c))
}

export function hasAll(set: PermSet, codes: readonly string[]): boolean {
  return codes.every((c) => set.has(c))
}

/**
 * 菜单裁剪。后端下发的 `menus` 已经只含可见项，这里只做兜底与排序 ——
 * 但**必须保留这层兜底**：将来若菜单来源换成静态表，裁剪逻辑还在原地。
 */
export function visibleMenus(menus: readonly MenuNode[], set: PermSet): MenuNode[] {
  return menus
    .filter((m) => set.has(m.code))
    .map((m) => ({ ...m, children: visibleMenus(m.children ?? [], set) }))
    .sort((a, b) => a.sortOrder - b.sortOrder)
}

/**
 * 空态该说什么。
 *
 * <p>★ 后端数据权限不足时返回的是 **200 + 空数组**（`1=0`，3005 从未被抛出），
 * 与"真的没数据"完全无法区分。不显式提示范围的话，
 * SELF 范围的人看列表会以为"公司真的只有我一个人"。
 */
export function emptyHint(scope: DataScopeType, prefixCount: number): string {
  if (scope === 'NONE') return '你当前没有可见的数据范围，请联系管理员'
  if (scope === 'ALL') return '暂无数据'
  if (scope === 'SELF') return '暂无数据（当前数据范围：仅本人）'
  return `暂无数据（当前数据范围：${scope === 'ORG' ? '仅本部门' : '本部门及下级'}，${prefixCount} 个范围）`
}

/** 未认证的判定。★ 后端 `/me/permissions` 是 @PublicApi，未认证返回 200 + 空清单而非 401。 */
export function isAnonymous(p: Pick<MyPermissions, 'userId'> | null | undefined): boolean {
  return !p || p.userId == null
}
