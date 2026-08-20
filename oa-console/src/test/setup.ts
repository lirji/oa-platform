import { installMatchMedia } from './viewport'
import '@testing-library/jest-dom'

// jsdom 未实现带伪元素参数的 getComputedStyle;antd 量测滚动条会以伪元素调用它 → 吞掉该噪声(不影响断言)。
const _getComputedStyle = window.getComputedStyle.bind(window)
window.getComputedStyle = ((elt: Element) => _getComputedStyle(elt)) as typeof window.getComputedStyle

// antd 的 Grid.useBreakpoint / useAppBreakpoint 都依赖 matchMedia;jsdom 未实现。
// ★ 桩会按 window.innerWidth 真的求值 —— 恒 false 的桩会让"窄屏应该降级"这类断言
//   全部假绿(所有断点都不命中,组件永远走同一条分支)。见 src/test/viewport.ts。
installMatchMedia()

// jsdom 默认 1024(D 档)。逐个测试用 setViewportWidth() 覆盖。
