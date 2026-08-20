import { useMemo } from 'react'
import { hasAll, hasAny } from '@oa/shared/perm/evaluate'
import { DATA_SCOPE_LABEL } from '@oa/shared/perm/types'
import { usePermStore } from '../store/permStore'

/**
 * **读权限的唯一入口**。
 *
 * <p>返回对象而不是 `usePerm(code): boolean`：后者在一个组件里判 5 个权限就是 5 次 hook 调用，
 * 且没法在条件分支里用。
 *
 * <p>纪律：任何地方**直接读 store 的 codes** 都算越界 —— Set 化、提权判断、
 * 数据范围语义必须收敛在这一处，否则规则会各处各一份。
 */
export function usePerm() {
  const codes = usePermStore((s) => s.codes)
  const elevated = usePermStore((s) => s.elevated)
  const loaded = usePermStore((s) => s.loaded)
  const anonymous = usePermStore((s) => s.anonymous)
  const dataScope = usePermStore((s) => s.dataScope)
  const scopePrefixes = usePermStore((s) => s.scopePrefixes)
  const moduleScope = usePermStore((s) => s.moduleScope)
  const delegators = usePermStore((s) => s.delegators)
  const elevations = usePermStore((s) => s.elevations)
  const menus = usePermStore((s) => s.menus)

  return useMemo(() => ({
    /** 加载完了吗。★ 与"没权限"是两件事：加载中不该渲染 403。 */
    loaded,
    /** 未认证（后端返回了空清单）。 */
    anonymous,
    /** 后端下发的菜单（已带 route/icon/sortOrder，前端不再维护第二份）。 */
    menus,
    has: (code: string) => codes.has(code),
    hasAny: (list: readonly string[]) => hasAny(codes, list),
    hasAll: (list: readonly string[]) => hasAll(codes, list),
    /** 该权限点当前是否处于活跃的 JIT 提权状态。 */
    isElevated: (code: string) => elevated.has(code),
    dataScope,
    dataScopeLabel: DATA_SCOPE_LABEL[dataScope],
    scopePrefixes,
    /** 某个模块的数据范围（可能比合并后的窄）。 */
    scopeOf: (module: string) => moduleScope[module] ?? dataScope,
    /** 我正在代理谁。非空时界面要显式提示，否则会"操作到别人头上而不自知"。 */
    delegators,
    /** 活跃提权，含剩余毫秒。倒计时用它。 */
    elevations,
    /** 最近一条提权的剩余毫秒；没有则 0。 */
    elevationRemainingMs: elevations.length
      ? Math.max(0, Math.min(...elevations.map((e) => e.remainingMs)))
      : 0,
  }), [codes, elevated, loaded, anonymous, menus, dataScope, scopePrefixes, moduleScope, delegators, elevations])
}
