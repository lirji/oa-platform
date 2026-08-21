import { expect, test } from '@playwright/test'
import { actAs, backend, fx, gotoAs, type Envelope } from './helpers'

/**
 * 验收 A8（数据范围为空时空态显示范围）与 A9（3002 走提权闭环），
 * 外加"诚实守卫"里能真正落地的那一条。
 */

/**
 * ★ 夹具里的 employee（`seed-user-10000`）**不是** SELF 范围 ——
 * 它正好落在那条"271 号组织含下级"授权的子树里，因此继承到了 ORG_AND_SUB。
 * 那条授权是夹具**故意**造的（沙盘要靠它展示部门继承），所以这里不能拿它当 SELF 样本。
 * manager（`seed-user-65`）同理：它还有一条 HR_ADMIN/ALL 的临时授权，实际是 ALL。
 *
 * 于是这里自己造一个真正 SELF 的账号：`seed-user-9997` 在 271 的子树之外、且原本没有任何授权。
 */
const SELF_USER = 'seed-user-9997'
const ROLE_EMPLOYEE = 4

test.describe('A8 · 看到的是全部还是一部分，必须说清', () => {
  // 后端数据权限不足时不抛错：`@DataScope` 算不出范围就生成 `1=0`，返回 200 + 空数组
  // （3005 在整个后端从未被抛出过）。于是"无权看"与"确实没有"在前端完全无法区分 ——
  // 这一组断言守的就是"前端必须自己说清楚"。
  let selfGrant = 0

  test.beforeAll(async ({ playwright }) => {
    const api = await playwright.request.newContext({ baseURL: 'http://127.0.0.1:5373' })
    const r = await api.post('/api/v1/iam/grants', {
      headers: { 'X-OA-User': fx.super, 'Content-Type': 'application/json' },
      data: { subjectType: 'USER', subjectId: SELF_USER, roleId: ROLE_EMPLOYEE, scopeType: 'SELF' },
    })
    selfGrant = (await r.json() as Envelope<number>).data
    await api.dispose()
  })

  test.afterAll(async ({ playwright }) => {
    const api = await playwright.request.newContext({ baseURL: 'http://127.0.0.1:5373' })
    if (selfGrant) await api.delete(`/api/v1/iam/grants/${selfGrant}`, { headers: { 'X-OA-User': fx.super } })
    await api.dispose()
  })

  test('SELF 与 ORG_AND_SUB 两个人看到的范围提示不同', async ({ page, browser }) => {
    await gotoAs(page, SELF_USER, '/org/employees')
    const selfText = await page.locator('.ant-alert-message').first().innerText()

    const ctx = await browser.newContext()
    const p2 = await ctx.newPage()
    await gotoAs(p2, fx.employee, '/org/employees')
    const mgrText = await p2.locator('.ant-alert-message').first().innerText()
    await ctx.close()

    expect(selfText, `两个不同 dataScope 的人看到了同一句提示：${selfText}`).not.toBe(mgrText)
    expect(selfText).toContain('仅本人')
    expect(mgrText).toContain('本部门及下级')
  })

  test('ALL 范围的人不显示范围条（否则噪音常驻）', async ({ page }) => {
    await gotoAs(page, fx.super, '/org/employees')
    await expect(page.getByText('当前数据范围')).toHaveCount(0)
  })

  test('范围条常驻，说明"看不到不等于不存在"', async ({ page }) => {
    await gotoAs(page, SELF_USER, '/org/employees')
    await expect(page.getByText('看不到某条记录不代表它不存在')).toBeVisible()
  })
})

// ★ 串行：一个用例在提权，另一个在断言"未提权应被拒" —— 并行跑必然互相打架。
//   这类竞态的表现是"单独跑都绿、一起跑随机红"，最容易被归咎为"环境不稳"。
test.describe.configure({ mode: 'serial' })

test.describe('A9 · 高危操作走 JIT 提权闭环', () => {
  test('持 SUPER_ADMIN 直查审计仍被拦，且弹出提权对话框', async ({ page }) => {
    await gotoAs(page, fx.super, '/report/audit')

    // ★ 3002 不是 3001：他**有**这个权限点，只是还没提权。
    //   两者混为一谈的话，界面会对着一个其实办得到的人说"你无权"。
    const resp = await backend(page, fx.super).get('/api/v1/report/audit?size=1')
    expect(resp.status()).toBe(403)
    expect((await resp.json() as Envelope).code).toBe(3002)

    await expect(page.getByText('申请临时提权')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByLabel('事由')).toBeVisible()   // 提权必须说明事由
  })

  test('提权后放行、撤回后立刻恢复拦截', async ({ page }) => {
    await gotoAs(page, fx.super, '/')
    const b = backend(page, fx.super)

    const before = await b.get('/api/v1/report/audit?size=1')
    expect((await before.json() as Envelope).code, '起点必须是未提权').toBe(3002)

    const el = await b.post('/api/v1/iam/elevations', { roleId: 1, reason: 'e2e 验收 A9', hours: 1 })
    const grantId = (await el.json() as Envelope<number>).data
    try {
      const after = await b.get('/api/v1/report/audit?size=1')
      expect(after.status(), '提权后应放行').toBe(200)
    } finally {
      // ★★★ 必须撤掉。JIT 提权默认活 1 小时，会跨多次运行继续有效，
      //   让后面每一条"未提权应被拒"的断言假失败 —— 而且是**下一次**才失败，
      //   最难联想到是这一次留下的。走 API 撤（裸 SQL 不会 bump epoch）。
      await b.del(`/api/v1/iam/grants/${grantId}`)
    }
    // 撤权走全局 epoch + 失效协议，后端保证的是"1 秒内"，不是"同一微秒"。
    // 不给这点时间就断言，测的是时钟而不是行为。
    await expect.poll(
      async () => (await (await b.get('/api/v1/report/audit?size=1')).json() as Envelope).code,
      { timeout: 3000, message: '撤回后 3 秒内必须恢复拦截' },
    ).toBe(3002)
  })
})

test.describe('诚实守卫', () => {
  test('影子校验不一致时，沙盘必须说出来而不是照常解释', async ({ page }) => {
    // 真造一次不一致要裸 SQL 改 grant_record（绕过失效协议），那会把环境搞成
    // 必须 FLUSHDB + 重启才能恢复的状态（静默失败清单第 9 条）。
    // 这里只需要验**界面在拿到 consistent=false 时的表现**，改响应即可。
    await actAs(page, fx.super)
    await page.route('**/iam/admin/explain*', async (route) => {
      const resp = await route.fetch()
      const body = await resp.json()
      if (body?.data) { body.data.consistent = false; body.data.cachedPermCount = 3; body.data.truthPermCount = 7 }
      await route.fulfill({ response: resp, body: JSON.stringify(body) })
    })
    await gotoAs(page, fx.super, `/iam/sandbox?userId=${fx.employee}&permCode=oa:employee:view`)

    await expect(page.getByText('缓存与数据库不一致')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('影子校验 不一致')).toBeVisible()
  })
})
