import { expect, test } from '@playwright/test'
import { backend, fx, gotoAs, type Envelope } from './helpers'

/**
 * 验收 A3（ADR-0008 强制验收）：**前端隐藏的东西，后端依然会拒绝**。
 *
 * <p>这条是整套 e2e 里最该有的一条。前端的 `<Can>` / `<PermRoute>` 只是体验层，
 * 真正的边界是后端的 `@RequiresPerm`；两者一旦只剩一个，系统就从"纵深"退化成
 * "看起来有权限控制"。Phase 8 的渗透用例在后端侧验过一遍，这里从**浏览器侧**再验一遍。
 */

/**
 * 高危端点清单。低权账号（EMPLOYEE / SELF）对它们必须一律 403 + code 3001。
 *
 * ★ **刻意不含 `DELETE /org/seed`**：万一判权真漏了，那一下会清掉一万条员工数据。
 *   渗透用例的目的是发现漏洞，不是在发现的同时把环境毁掉。
 *
 * ★ 也不含带 `@Valid` 请求体的接口（`POST /org/employees`、`/iam/delegations`、
 *   `/doc/official`）：Spring 的参数校验发生在切面之前，无权者拿到的是 **400 而不是 403**。
 *   把它们放进来会让这张表看起来"有些接口没判权"，其实只是没走到判权那一步。
 */
const FORBIDDEN: Array<{ label: string; run: (b: ReturnType<typeof backend>) => Promise<unknown>; perm: string }> = [
  { label: '看别人的权限解释', perm: 'oa:iam:admin', run: (b) => b.get(`/api/v1/iam/admin/explain?userId=${fx.hr}&permCode=oa:org:view`) },
  { label: '以他人视角预览',   perm: 'oa:iam:admin', run: (b) => b.get(`/api/v1/iam/admin/preview?userId=${fx.hr}`) },
  { label: '查授权来源链',     perm: 'oa:iam:admin', run: (b) => b.get(`/api/v1/iam/admin/why?userId=${fx.hr}&permCode=oa:org:view`) },
  { label: '看判权缓存统计',   perm: 'oa:iam:admin', run: (b) => b.get('/api/v1/iam/admin/cache-stats') },
  { label: '列举他人的授权',   perm: 'oa:iam:view',  run: (b) => b.get(`/api/v1/iam/grants?subjectType=USER&subjectId=${fx.hr}`) },
  { label: '查审计日志',       perm: 'oa:audit:view', run: (b) => b.get('/api/v1/report/audit?size=1') },
  { label: '★ 给自己授权',     perm: 'oa:iam:grant', run: (b) => b.post('/api/v1/iam/grants', { subjectType: 'USER', subjectId: fx.employee, roleId: 1, scopeType: 'ALL' }) },
  { label: '建组织',           perm: 'oa:org:create', run: (b) => b.post('/api/v1/org/units', { code: 'E2E-HACK', name: '越权测试', type: 'DEPARTMENT', parentId: 1 }) },
  { label: '给自己提权',       perm: 'oa:iam:elevate', run: (b) => b.post('/api/v1/iam/elevations', { roleId: 1, reason: 'e2e', hours: 1 }) },
  { label: '撤销他人的授权',   perm: 'oa:iam:revoke', run: (b) => b.del('/api/v1/iam/grants/999999') },
  { label: '强制刷审计缓冲',   perm: 'oa:report:view', run: (b) => b.post('/api/v1/report/audit/flush') },
]

test.describe('A3 · 前端藏了，后端也拦得住', () => {
  test('低权用户看不到管理入口，且直连接口一律 403/3001', async ({ page }) => {
    await gotoAs(page, fx.employee, '/')

    // ① 界面侧：菜单里没有权限中心，直接输地址也进不去
    await expect(page.getByRole('menuitem', { name: /权限中心/ })).toHaveCount(0)
    await page.goto('/iam')
    await expect(page.getByText('无访问权限')).toBeVisible()

    // ② 接口侧：同一个上下文里绕过前端直连
    const b = backend(page, fx.employee)
    for (const c of FORBIDDEN) {
      const resp = await c.run(b) as Awaited<ReturnType<typeof b.get>>
      const body = await resp.json() as Envelope
      expect(resp.status(), `${c.label}（${c.perm}）应当 403`).toBe(403)
      expect(body.code, `${c.label}（${c.perm}）应当是 3001 权限不足`).toBe(3001)
    }
  })

  test('清单本身没有缩水（少于 8 条就不叫数据驱动了）', async () => {
    expect(FORBIDDEN.length).toBeGreaterThanOrEqual(8)
  })

  test('高权用户对同一批接口是通的（否则上一条可能只是"接口挂了"）', async ({ page }) => {
    // ★ 没有这条对照，上面那条在"后端全挂"时同样全绿 —— 403 与 503 都不是 200。
    await gotoAs(page, fx.super, '/')
    const b = backend(page, fx.super)
    const resp = await b.get(`/api/v1/iam/admin/why?userId=${fx.employee}&permCode=oa:employee:view`)
    expect(resp.status()).toBe(200)
    const body = await resp.json() as Envelope<{ sources: unknown[] }>
    expect(body.code).toBe(0)
    expect(body.data.sources.length).toBeGreaterThan(0)
  })
})
