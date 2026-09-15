/**
 * 组织树的纯逻辑（不含 React / antd）。抽出来是为了能表驱动地测 ——
 * 成环判定写在组件闭包里的话，唯一的验证方式是真的去拖一次。
 */

/** 判定只需要 id 与 path 两个字段，不必依赖整个 OrgNode。 */
export interface TreePathNode {
  id: number
  path: string
  children?: readonly TreePathNode[]
}

/** 把嵌套树摊平成 id → 节点 的索引。 */
export function indexTree<T extends TreePathNode>(nodes: readonly T[]): Map<number, T> {
  const m = new Map<number, T>()
  const walk = (ns: readonly T[]) => {
    for (const n of ns) {
      m.set(n.id, n)
      walk((n.children ?? []) as readonly T[])
    }
  }
  walk(nodes)
  return m
}

/** 把某节点的子树换成懒加载结果，其余结构不动。 */
export function attachChildren<T extends { id: number; children?: readonly T[] }>(
  nodes: readonly T[],
  id: number,
  children: T[],
): T[] {
  return nodes.map((n) => {
    if (n.id === id) return { ...n, children }
    if (!n.children?.length) return n
    return { ...n, children: attachChildren(n.children, id, children) }
  })
}

/** 按名称/编码过滤，命中节点的祖先一并保留，便于树搜索后仍能展开。 */
export function filterTree<T extends { name: string; code: string; children?: readonly T[] }>(
  nodes: readonly T[],
  keyword: string,
): T[] {
  const q = keyword.trim().toLocaleLowerCase('zh-CN')
  if (!q) return nodes as T[]
  const keep = (n: T): T | null => {
    const kids = (n.children ?? []).map(keep).filter((c): c is T => c != null)
    const hit = n.name.toLocaleLowerCase('zh-CN').includes(q)
      || n.code.toLocaleLowerCase('zh-CN').includes(q)
    if (!hit && kids.length === 0) return null
    return { ...n, children: kids }
  }
  return nodes.map(keep).filter((n): n is T => n != null)
}

/** 收集树上全部 id，搜索命中后用来展开祖先。 */
export function collectIds<T extends { id: number; children?: readonly T[] }>(nodes: readonly T[]): number[] {
  const ids: number[] = []
  const walk = (ns: readonly T[]) => {
    for (const n of ns) {
      ids.push(n.id)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(nodes)
  return ids
}

/**
 * 通讯录条目是否属于某组织（本级或下级）。
 *
 * <p>目录接口没有 orgId 过滤，只能前端用 path 前缀筛。正确性同样依赖 path 尾斜杠。
 */
export function inOrgSubtree(
  entry: { orgId?: number | null; orgPath?: string | null },
  org: { id: number; path: string },
  directOnly = false,
): boolean {
  if (entry.orgId === org.id) return true
  if (directOnly) return false
  const p = entry.orgPath
  if (!p) return false
  return p.startsWith(org.path)
}

/** 新建下级时的类型建议；类型只是标签，后端不强制父子组合。 */
export function suggestedChildType(parentType: string): string {
  switch (parentType) {
    case 'GROUP': return 'COMPANY'
    case 'COMPANY':
    case 'BU':
    case 'CENTER': return 'DEPT'
    case 'DEPT': return 'TEAM'
    case 'TEAM': return 'SQUAD'
    default: return 'TEAM'
  }
}

/**
 * 成环预判：不能把一个节点拖进它自己或它的后代。
 *
 * <p>用 path 前缀判，与后端 `org_path LIKE '前缀%'` 是同一套语义（硬约束第 4 条）。
 *
 * <p>★ **这个判定的正确性完全依赖 path 的尾斜杠**。后端保证 path 恒为 `/a/b/c/`
 * 形式（`OrgUnitService` 的类注释第一条）。少了尾斜杠的话，
 * `/1/23/` 会被判成 `/1/2` 的后代 —— 于是"把部门拖进 23 号部门"被禁掉，
 * 而 2 号和 23 号之间毫无关系。这种错只在 id 恰好是另一个 id 的前缀时出现，
 * 小数据集上永远测不出来。
 *
 * <p>找不到节点时返回 false（宁可禁掉一次合法拖拽，也不要放过一次成环）。
 */
export function canDrop(
  dragId: number,
  dropId: number,
  index: ReadonlyMap<number, TreePathNode>,
): boolean {
  const drag = index.get(dragId)
  const drop = index.get(dropId)
  if (!drag || !drop) return false
  if (dragId === dropId) return false
  return !drop.path.startsWith(drag.path)
}
