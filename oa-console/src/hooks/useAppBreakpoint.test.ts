import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { resetViewportRegistry, setViewportWidth } from '../test/viewport'
import { currentTier, describeTier, useAppBreakpoint, type Tier } from './useAppBreakpoint'

afterEach(resetViewportRegistry)

describe('档位边界（计划 §7 断点表）', () => {
  // 边界值逐个钉住：off-by-one 在这类表上最常见，而它的表现是
  // "1440 的屏幕莫名其妙折叠了侧栏" —— 用起来别扭，但没人会去报 bug。
  const cases: Array<[number, Tier]> = [
    [2560, 'A'], [1600, 'A'],
    [1599, 'B'], [1440, 'B'],
    [1439, 'C'], [1366, 'C'], [1280, 'C'],
    [1279, 'D'], [1024, 'D'], [992, 'D'],
    [991, 'E'], [768, 'E'],
    [767, 'F'], [375, 'F'],
  ]
  for (const [w, tier] of cases) {
    it(`${w}px → ${tier} 档`, () => {
      setViewportWidth(w)
      expect(currentTier()).toBe(tier)
    })
  }

  it('1366×768 落在 C 档（验收 A7 量的就是这个视口）', () => {
    expect(currentTier(1366)).toBe('C')
  })

  it('antd 的 xxl(1600) 不能当窄屏分界 —— B 档会被误判', () => {
    // 这条钉的是**被这个 hook 取代的那个写法**：`!screens.xxl` 在 1440–1599 为真，
    // 于是整个 B 档（计划要求侧栏展开、三栏全开）被当成窄屏。
    expect(describeTier(currentTier(1440)).siderMode).toBe('expanded')
    expect(describeTier(currentTier(1440)).inspectorMode).toBe('inline')
  })
})

describe('结构决策', () => {
  const at = (w: number) => describeTier(currentTier(w))

  it('侧栏：A/B 展开 · C/D 折叠 · E/F 抽屉', () => {
    expect(at(1600).siderMode).toBe('expanded')
    expect(at(1440).siderMode).toBe('expanded')
    expect(at(1366).siderMode).toBe('collapsed')
    expect(at(1024).siderMode).toBe('collapsed')
    expect(at(900).siderMode).toBe('drawer')
    expect(at(400).siderMode).toBe('drawer')
  })

  it('只有 D 档锁死折叠（C 档用户还能自己展开）', () => {
    expect(at(1366).siderLocked).toBe(false)
    expect(at(1024).siderLocked).toBe(true)
  })

  it('Inspector：inline → overlay(C) → tabs(D) → blocked(E/F)', () => {
    expect(at(1600).inspectorMode).toBe('inline')
    expect(at(1366).inspectorMode).toBe('overlay')
    expect(at(1024).inspectorMode).toBe('tabs')
    expect(at(900).inspectorMode).toBe('blocked')
  })

  it('C 档起表格 pageSize 20 → 10', () => {
    expect(at(1440).tablePageSize).toBe(20)
    expect(at(1366).tablePageSize).toBe(10)
  })

  it('E/F 降级重交互（提示 + 只读，不是拦截）', () => {
    expect(at(1024).interactionsDegraded).toBe(false)
    expect(at(900).interactionsDegraded).toBe(true)
  })
})

describe('useAppBreakpoint()', () => {
  it('首帧就是对的 —— 不需要 useEffect 追赶', () => {
    // Grid.useBreakpoint() 首次渲染返回 {}，于是 useState 初值会定死在
    // "未解析"那一帧上。这条断言就是钉住"第一次 render 拿到的已经是 C"。
    setViewportWidth(1366)
    const { result } = renderHook(() => useAppBreakpoint())
    expect(result.current.tier).toBe('C')
    expect(result.current.inspectorMode).toBe('overlay')
  })

  it('视口变化会重渲染并给出新档位', () => {
    setViewportWidth(1920)
    const { result } = renderHook(() => useAppBreakpoint())
    expect(result.current.tier).toBe('A')
    act(() => { setViewportWidth(1366) })
    expect(result.current.tier).toBe('C')
    act(() => { setViewportWidth(800) })
    expect(result.current.siderMode).toBe('drawer')
  })

  it('同一档内改宽度不产生新对象（拖窗口不该刷屏）', () => {
    setViewportWidth(1300)
    const { result } = renderHook(() => useAppBreakpoint())
    const first = result.current
    act(() => { setViewportWidth(1420) }) // 仍在 C 档
    expect(result.current).toBe(first)
  })

  it('atLeast / atMost 说的是【宽度】，不是字母顺序', () => {
    // ★ 档位字母 A→F 是**由宽到窄**，与"字母变大"的直觉相反。
    //   在 C 档：atMost('B') 为真（C 比 B 窄），atMost('D') 为假（C 比 D 宽）。
    //   写反的后果是降级分支挂错档 —— 宽屏被当窄屏或反之，两种都很难一眼看出来。
    setViewportWidth(1366) // C
    const { result } = renderHook(() => useAppBreakpoint())
    expect(result.current.atLeast('C')).toBe(true)
    expect(result.current.atLeast('D')).toBe(true)   // C 比 D 宽 → 满足"至少 D 那么宽"
    expect(result.current.atLeast('B')).toBe(false)
    expect(result.current.atMost('C')).toBe(true)
    expect(result.current.atMost('B')).toBe(true)    // C 比 B 窄 → 满足"至多 B 那么宽"
    expect(result.current.atMost('D')).toBe(false)
  })
})
