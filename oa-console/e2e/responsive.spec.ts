import { expect, test } from '@playwright/test'
import { fx, gotoAs } from './helpers'

/**
 * 验收 A7：**1366×768 无页面级横向滚动、主 CTA 可见**。
 *
 * <p>★ 用 `test.use({ viewport })` 分组，而不是配成两个 project：
 * project 之间是并行的，而 `live-perm.spec.ts` 会真的授权/撤权 ——
 * 同一套用例在两个 project 里同时跑同一个账号会互相踩。
 * 计划要的是"别只测一个视口"，这里照办；实现方式换成分组是为了不引入竞态。
 */
const ROUTES = [
  { path: '/', name: '工作台' },
  { path: '/org', name: '组织管理' },
  { path: '/org/employees', name: '员工管理' },
  { path: '/org/directory', name: '通讯录' },
  { path: '/kb', name: '知识库' },
  { path: '/iam', name: '角色与授权' },
  { path: '/iam/sandbox', name: '权限沙盘' },
  { path: '/report', name: '管理驾驶舱' },
]

const VIEWPORTS = [
  { label: '1920×1080（A 档）', width: 1920, height: 1080 },
  { label: '1366×768（C 档 —— 硬验收就量它）', width: 1366, height: 768 },
]

for (const vp of VIEWPORTS) {
  test.describe(`A7 · ${vp.label}`, () => {
    test.use({ viewport: { width: vp.width, height: vp.height } })

    for (const r of ROUTES) {
      test(`${r.name} 无页面级横向滚动`, async ({ page }) => {
        await gotoAs(page, fx.super, r.path)
        await page.waitForTimeout(300)   // 等布局稳定（antd 的 Table/Tree 会二次量宽）

        const overflow = await page.evaluate(() => ({
          scrollWidth: document.documentElement.scrollWidth,
          clientWidth: document.documentElement.clientWidth,
        }))
        // 宽表格允许自己内部横向滚动，但**页面本身**不许 —— 后者会让侧栏和顶栏一起跑掉。
        expect(overflow.scrollWidth, `${r.name} 溢出 ${overflow.scrollWidth - overflow.clientWidth}px`)
          .toBeLessThanOrEqual(overflow.clientWidth)
      })
    }

    // ★ 显式列出"应当有主 CTA"的页面，而不是"扫到几个算几个"：
    //   后者在标记全丢时会一次循环都不跑，变成一条永远通过的检查。
    for (const path of ['/org/directory', '/kb', '/iam', '/iam/sandbox']) {
      test(`${path} 的主 CTA 在首屏内`, async ({ page }) => {
        await gotoAs(page, fx.super, path)
        const cta = page.locator('[data-testid="primary-action"]').first()
        // 必须等它出现：路由是懒加载的，permissions 回来时页面 chunk 可能还在下载。
        await expect(cta).toBeVisible({ timeout: 10_000 })
        const box = await cta.boundingBox()
        expect(box!.y, `主 CTA 在 ${box!.y}px，超出了 ${vp.height}px 的视口`).toBeLessThan(vp.height)
      })
    }
  })
}
