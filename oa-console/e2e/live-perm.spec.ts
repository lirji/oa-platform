import { expect, test, type APIRequestContext } from '@playwright/test'
import { fx, gotoAs, type Envelope } from './helpers'

/**
 * 验收 A1：**改角色后不刷新即生效**。
 *
 * <p>★ 用一个**专属账号**（`seed-user-9999`）而不是夹具里的 employee ——
 * Playwright 的用例文件之间是并行的，如果这里给 employee 加了 HR_ADMIN，
 * 隔壁 `perm-boundary.spec.ts` 正好在断言"employee 是低权用户"，就会随机红。
 * 这类失败一天出现一次、重跑就好，最后训练出的是"红了就重跑"的习惯。
 */
const SUBJECT = 'seed-user-9999'
const ROLE_HR = 2      // HR_ADMIN
const ROLE_EMP = 4     // EMPLOYEE
/** HR_ADMIN 比 EMPLOYEE 多出来的那个菜单。 */
const HR_ONLY_MENU = /管理驾驶舱/

async function grant(api: APIRequestContext, roleId: number): Promise<number> {
  const r = await api.post('/api/v1/iam/grants', {
    headers: { 'X-OA-User': fx.super, 'Content-Type': 'application/json' },
    data: { subjectType: 'USER', subjectId: SUBJECT, roleId, scopeType: 'ALL' },
  })
  const b = await r.json() as Envelope<number>
  expect(b.code, '授权失败，后面的断言无从谈起').toBe(0)
  return b.data
}

async function revoke(api: APIRequestContext, grantId: number) {
  const r = await api.delete(`/api/v1/iam/grants/${grantId}`, {
    headers: { 'X-OA-User': fx.super },
    failOnStatusCode: false,
  })
  return r.status()
}

/**
 * 触发一次"用户在操作"。
 *
 * <p>权限变更靠三条腿传到前端（ADR-C4），Phase 3 实现了两条：响应头比对与
 * 焦点/重连重取。**页面完全静止时两条腿都不会动** —— 这不是缺陷，是
 * "不轮询"的代价（万人 / 15 秒 = 667 QPS 只为一个几乎不变的东西）。
 * 所以这里派发 focus，走的正是第二条腿。
 */
async function userActs(page: import('@playwright/test').Page) {
  await page.evaluate(() => {
    // ★ 必须派发到 **window** 上：react-query v5 的 focusManager 注册的是
    //   `window.addEventListener('visibilitychange', ...)`，而 `new Event()`
    //   默认 bubbles=false —— 派发到 document 上根本传不上去，
    //   表现是"事件发了、什么也没发生"，最像"功能坏了"的一种测试失败。
    window.dispatchEvent(new Event('visibilitychange'))
    window.dispatchEvent(new Event('focus'))
  })
}

test.describe.configure({ mode: 'serial' })

test.describe('A1 · 改角色后不刷新即生效', () => {
  let baseGrant = 0
  let hrGrant = 0

  test.beforeAll(async ({ playwright }) => {
    const api = await playwright.request.newContext({ baseURL: 'http://127.0.0.1:5373' })
    baseGrant = await grant(api, ROLE_EMP)   // 先给个底子，否则空清单会被判成"未认证"
    await api.dispose()
  })

  test.afterAll(async ({ playwright }) => {
    const api = await playwright.request.newContext({ baseURL: 'http://127.0.0.1:5373' })
    if (hrGrant) await revoke(api, hrGrant)
    if (baseGrant) await revoke(api, baseGrant)
    await api.dispose()
  })

  test('授权 → 下一次交互后菜单出现；撤权 → 菜单消失；全程没有整页刷新', async ({ page }) => {
    await gotoAs(page, SUBJECT, '/')

    // 在 window 上留个记号：整页刷新会把它冲掉。
    // ★ 不用 page.on('framenavigated') 计数 —— SPA 的 pushState 也会触发它，
    //   而"点了个菜单"与"整页重载"恰恰是这条验收要区分的两件事。
    await page.evaluate(() => { (window as unknown as Record<string, unknown>).__e2eAlive = true })

    await expect(page.getByRole('menuitem', { name: HR_ONLY_MENU })).toHaveCount(0)

    // ① 授权：后端不保证秒级，判据放宽到"下一次交互后"
    hrGrant = await grant(page.request, ROLE_HR)
    await userActs(page)
    await expect(page.getByRole('menuitem', { name: HR_ONLY_MENU })).toBeVisible({ timeout: 5000 })

    // ② 撤权：走全局 epoch，后端 1 秒内必然生效
    const status = await revoke(page.request, hrGrant)
    expect(status).toBe(200)
    hrGrant = 0
    await userActs(page)
    await expect(page.getByRole('menuitem', { name: HR_ONLY_MENU })).toHaveCount(0, { timeout: 5000 })

    // ③ 全程没有整页刷新
    expect(await page.evaluate(() => (window as unknown as Record<string, unknown>).__e2eAlive)).toBe(true)
  })

  test('★ 权限变化不会引发请求风暴（回归）', async ({ page }) => {
    // 这条是被 A1 逼出来的：原实现只拿响应头与 store 比对，而重取的响应自己
    // 也带这个头、store 又要等 effect 刷完才更新 —— 每个响应都再触发一次失效。
    // 实测一次焦点事件 2 秒内打出 840 个 /me/permissions（约 420 QPS，单标签页）。
    // 它不报错、不白屏，只是"页面越用越卡"。
    let hits = 0
    page.on('request', (r) => { if (r.url().includes('/me/permissions')) hits++ })
    await gotoAs(page, SUBJECT, '/')

    const g = await grant(page.request, ROLE_HR)
    try {
      await userActs(page)
      await expect(page.getByRole('menuitem', { name: HR_ONLY_MENU })).toBeVisible({ timeout: 5000 })
      await page.waitForTimeout(1500)
      expect(hits, `2 秒内发了 ${hits} 次 /me/permissions`).toBeLessThanOrEqual(5)
    } finally {
      await revoke(page.request, g)
    }
  })

  test('撤权后连接口也立刻拒绝（前端消失不等于后端收紧）', async ({ page }) => {
    // A1 只说了界面。真正要紧的是后端那一侧也同步了 —— 否则"菜单没了但接口还通"
    // 是最糟的一种：看起来收权成功了。
    await gotoAs(page, SUBJECT, '/')
    const g = await grant(page.request, ROLE_HR)
    const ok = await page.request.get('/api/v1/report/overview', { headers: { 'X-OA-User': SUBJECT }, failOnStatusCode: false })
    expect(ok.status(), '刚授权就该通').toBe(200)

    expect(await revoke(page.request, g)).toBe(200)
    const denied = await page.request.get('/api/v1/report/overview', { headers: { 'X-OA-User': SUBJECT }, failOnStatusCode: false })
    expect(denied.status(), '撤权后必须立刻 403（epoch bump + 失效协议）').toBe(403)
    expect((await denied.json() as Envelope).code).toBe(3001)
  })
})
