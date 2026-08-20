import { useMemo, useSyncExternalStore } from 'react'

/**
 * oa-console 的断点档位（计划 §7）。
 *
 * ## 为什么不直接用 antd 的 `Grid.useBreakpoint()`
 *
 * §7 的档位锚定 antd 默认值，但**多一档自定义的 1440**：
 * B 档（1440–1599）要求三栏全开、侧栏展开；C 档（1280–1439，1366×768 落这里）
 * 才折叠侧栏、收起右栏。antd 最近的两个断点是 `xl`(1200) 和 `xxl`(1600) ——
 * 用 `!screens.xxl` 当"窄屏"会把整个 B 档也判成窄屏，
 * 1440 的屏幕白白损失一个展开的侧栏和一整栏 Inspector。
 *
 * ## 只做结构决策，不做显隐
 *
 * §7 的三条工程底线之一：**纯显隐用 CSS media query，不要用 JS `isMobile`**
 * （避免闪烁，也避免 jsdom 里的 matchMedia 问题）。
 * 这个 hook 只回答那些**改变组件结构**的问题 —— 侧栏是折叠还是 Drawer、
 * Inspector 是并排还是 overlay、表格一页几条 —— 这些用 CSS 表达不了。
 *
 * ## 首帧就是对的
 *
 * `Grid.useBreakpoint()` 首次渲染返回 `{}`（所有断点 undefined），
 * 于是 `useState(!narrow)` 会把初值定死在"未解析"那一帧上，
 * 要再补一个 `useEffect` 去追 —— `SandboxPage` 里那段注释记的就是这个坑。
 * `useSyncExternalStore` 的 `getSnapshot` 是同步读 matchMedia 的，首帧即正确，
 * 不需要那层追赶。
 */
export type Tier = 'A' | 'B' | 'C' | 'D' | 'E' | 'F'

/** 档位下界（px）。顺序即从宽到窄，`currentTier()` 依赖这个顺序。 */
export const TIER_MIN_WIDTH: ReadonlyArray<readonly [Tier, number]> = [
  ['A', 1600],
  ['B', 1440],
  ['C', 1280],
  ['D', 992],
  ['E', 768],
  ['F', 0],
]

const ORDER: readonly Tier[] = ['A', 'B', 'C', 'D', 'E', 'F']

/** 侧栏形态。`collapsed` 与 `drawer` 的区别是结构性的：后者根本不占布局。 */
export type SiderMode = 'expanded' | 'collapsed' | 'drawer'

/**
 * 沙盘右栏（Inspector）形态。
 * `blocked` 不是"拦截" —— §7 底线③：窄屏降级一律"提示 + 只读"，
 * 页面仍然渲染解释结果，只是不再提供三栏联动。
 */
export type InspectorMode = 'inline' | 'overlay' | 'tabs' | 'blocked'

export interface AppBreakpoint {
  tier: Tier
  /**
   * "至少有某档那么宽"。★ 档位字母 A→F 是**由宽到窄**，与字母顺序的直觉相反：
   * 在 C 档，`atLeast('D')` 为**真**（C 比 D 宽），`atLeast('B')` 为假。
   */
  atLeast: (t: Tier) => boolean
  /** "至多有某档那么宽"。在 C 档，`atMost('B')` 为**真**，`atMost('D')` 为假。 */
  atMost: (t: Tier) => boolean
  siderMode: SiderMode
  /** D 档强制折叠，不给用户切换 —— 992–1279 展开侧栏会把内容区挤到没法用。 */
  siderLocked: boolean
  inspectorMode: InspectorMode
  /** C 档起可用高度只剩 ~620px，一页 20 条要滚两屏才能到分页器。 */
  tablePageSize: number
  /** E/F：拖拽这类重交互降级为只读 + Alert 说明，而不是拦住不让进。 */
  interactionsDegraded: boolean
}

export function currentTier(width?: number): Tier {
  if (typeof width === 'number') {
    for (const [t, min] of TIER_MIN_WIDTH) if (width >= min) return t
    return 'F'
  }
  if (typeof window === 'undefined') return 'B' // SSR 兜底：按最常见的桌面档渲染
  for (const [t, min] of TIER_MIN_WIDTH) {
    if (min === 0) return t
    if (window.matchMedia(`(min-width: ${min}px)`).matches) return t
  }
  return 'F'
}

function subscribe(onChange: () => void): () => void {
  if (typeof window === 'undefined' || !window.matchMedia) return () => {}
  const mqls = TIER_MIN_WIDTH.filter(([, min]) => min > 0).map(([, min]) =>
    window.matchMedia(`(min-width: ${min}px)`),
  )
  // 老 Safari 只有 addListener；两个都挂上（addEventListener 存在时优先）。
  for (const m of mqls) m.addEventListener ? m.addEventListener('change', onChange) : m.addListener(onChange)
  return () => {
    for (const m of mqls) m.removeEventListener ? m.removeEventListener('change', onChange) : m.removeListener(onChange)
  }
}

export function describeTier(tier: Tier): Omit<AppBreakpoint, 'tier' | 'atLeast' | 'atMost'> {
  const i = ORDER.indexOf(tier)
  const wider = (t: Tier) => i <= ORDER.indexOf(t)
  return {
    siderMode: wider('B') ? 'expanded' : wider('D') ? 'collapsed' : 'drawer',
    siderLocked: tier === 'D',
    inspectorMode: wider('B') ? 'inline' : tier === 'C' ? 'overlay' : tier === 'D' ? 'tabs' : 'blocked',
    tablePageSize: wider('B') ? 20 : 10,
    interactionsDegraded: !wider('D'),
  }
}

export function useAppBreakpoint(): AppBreakpoint {
  // getSnapshot 返回的是**字符串档位**而不是宽度：同一档内怎么拖窗口都不会重渲染。
  const tier = useSyncExternalStore(subscribe, () => currentTier(), () => 'B' as Tier)
  return useMemo(() => {
    const i = ORDER.indexOf(tier)
    return {
      tier,
      atLeast: (t: Tier) => i <= ORDER.indexOf(t),
      atMost: (t: Tier) => i >= ORDER.indexOf(t),
      ...describeTier(tier),
    }
  }, [tier])
}
