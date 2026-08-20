/**
 * jsdom 的 matchMedia 桩 —— **会真的按视口宽度求值**。
 *
 * <p>原来的桩是 `matches: false` 恒定值。它能让 antd 不炸，但会制造**假绿**：
 * 任何"窄屏应该降级"的断言在它下面都通过（因为所有断点都不命中，
 * 组件永远走同一条分支），于是测试证明的是"这段代码没崩"，
 * 而不是"C 档确实折叠了侧栏"。计划 §7 底线②点名了这件事。
 *
 * <p>这里实现 `(min-width: Npx)` / `(max-width: Npx)` 两种查询 —— 够 antd 的
 * Grid 与 `useAppBreakpoint()` 用。`setViewportWidth()` 会派发 change 事件，
 * 所以 `useSyncExternalStore` 与 antd 的 responsiveObserve 都会跟着更新。
 */
type Listener = (e: MediaQueryListEvent) => void

const registry: Array<{ query: string; listeners: Set<Listener>; mql: MediaQueryList }> = []

function evaluate(query: string, width: number): boolean {
  // 一条查询里可能有多个条件（and 连接），全部满足才算命中
  const conds = query.split(/\s+and\s+/i)
  return conds.every((c) => {
    const min = /\(min-width:\s*(\d+)px\)/i.exec(c)
    if (min) return width >= Number(min[1])
    const max = /\(max-width:\s*(\d+)px\)/i.exec(c)
    if (max) return width <= Number(max[1])
    return true // 不认识的条件（如 print）当作命中，别把它变成隐形的 false
  })
}

export function installMatchMedia(): void {
  window.matchMedia = (query: string): MediaQueryList => {
    const listeners = new Set<Listener>()
    const mql = {
      get matches() { return evaluate(query, window.innerWidth) },
      media: query,
      onchange: null,
      addEventListener: (_: string, l: Listener) => listeners.add(l),
      removeEventListener: (_: string, l: Listener) => listeners.delete(l),
      addListener: (l: Listener) => listeners.add(l),
      removeListener: (l: Listener) => listeners.delete(l),
      dispatchEvent: () => false,
    } as unknown as MediaQueryList
    registry.push({ query, listeners, mql })
    return mql
  }
}

/** 改视口宽度并通知所有监听者。返回值方便 `setViewportWidth(1366)` 后直接断言。 */
export function setViewportWidth(width: number): number {
  Object.defineProperty(window, 'innerWidth', { value: width, writable: true, configurable: true })
  for (const { query, listeners, mql } of registry) {
    const e = { matches: evaluate(query, width), media: query } as MediaQueryListEvent
    for (const l of [...listeners]) l.call(mql, e)
  }
  window.dispatchEvent(new Event('resize'))
  return width
}

/** 每个测试文件之间清掉注册表，避免上一个文件的组件残留监听。 */
export function resetViewportRegistry(): void {
  registry.length = 0
}
