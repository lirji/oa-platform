import { expect, test } from '@playwright/test'
import { fx, gotoAs } from './helpers'

/** 全量同步要拉 10000/500 = 20 页，给它宽裕些。 */
test.setTimeout(90_000)

const row = '[data-testid="dir-row"]'

async function fullySynced(page: import('@playwright/test').Page) {
  await gotoAs(page, fx.super, '/org/directory')
  await expect(page.locator(row).first()).toBeVisible({ timeout: 60_000 })
  // 标题里的人数就是 entries.length，等它稳定即认为全量拉完
  await expect(page.getByText(`${fx.employees} 人`)).toBeVisible({ timeout: 60_000 })
}

test.describe('通讯录', () => {
  test('一万人只渲染几十行（虚拟滚动确实生效）', async ({ page }) => {
    await fullySynced(page)
    const rendered = await page.locator(row).count()
    expect(rendered, `渲染了 ${rendered} 行`).toBeLessThan(60)
    expect(rendered).toBeGreaterThan(0)
  })

  test('A5 · 二次进入从本地缓存秒开（不等网络同步）', async ({ page }) => {
    await fullySynced(page)

    // 人为把增量同步拖慢到 2s：缓存有效的话，列表不该等它。
    let deltaSettled = false
    await page.route('**/directory/delta*', async (route) => {
      await new Promise((r) => setTimeout(r, 2000))
      deltaSettled = true
      await route.continue()
    })

    const t0 = Date.now()
    await page.reload()
    await expect(page.locator(row).first()).toBeVisible({ timeout: 1500 })
    const elapsed = Date.now() - t0

    // ★ 真正的判据是"在网络同步回来之前就画出来了"，而不是一个绝对毫秒数：
    //   这跑在 vite dev server 上（模块不打包），冷启本身就比生产慢好几倍，
    //   拿绝对值当门槛只会得到一条随机红的测试。
    expect(deltaSettled, `列表在 ${elapsed}ms 就可见，此时 delta 还没回来`).toBe(false)
    console.log(`  ⏱  缓存命中后首行可见：${elapsed}ms（delta 被拖到 2000ms）`)
  })

  test('两个标签页同时同步不会互相写坏', async ({ page, context }) => {
    await fullySynced(page)

    const second = await context.newPage()
    const errors: string[] = []
    for (const p of [page, second]) {
      p.on('pageerror', (e) => errors.push(String(e)))
      p.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
    }

    await Promise.all([page.reload(), second.goto('/org/directory')])
    await Promise.all([
      expect(page.getByText(`${fx.employees} 人`)).toBeVisible({ timeout: 60_000 }),
      expect(second.getByText(`${fx.employees} 人`)).toBeVisible({ timeout: 60_000 }),
    ])

    // IndexedDB 的并发写最典型的失败是 ConstraintError（同一 keyPath 撞了）
    expect(errors.filter((e) => /ConstraintError|TransactionInactive|AbortError/.test(e))).toEqual([])
    // 两个标签页看到的人数一致
    const [a, b] = await Promise.all([
      page.getByText(/\d+ 人/).first().innerText(),
      second.getByText(/\d+ 人/).first().innerText(),
    ])
    expect(a).toBe(`${fx.employees} 人`)
    expect(b).toBe(a)
    await second.close()
  })

  test('★ 陈旧提示常驻 —— 不显示同步时间的离线缓存等于骗人', async ({ page }) => {
    await fullySynced(page)
    await expect(page.getByText(/数据更新于/)).toBeVisible()
  })
})
