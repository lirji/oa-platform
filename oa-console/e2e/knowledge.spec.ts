import { expect, test } from '@playwright/test'
import { backend, fx, gotoAs, type Envelope } from './helpers'

test.describe('AC-02 · 知识库对象级判权', () => {
  test('列表、单篇详情和访问解释走真实接口', async ({ page }) => {
    const b = backend(page, fx.super)
    const response = await b.get('/api/v1/doc/kb?limit=20')
    const body = await response.json() as Envelope<Array<{ id: number; title: string }>>
    expect(body.data.length, 'Phase 7 夹具应至少有一篇可见文档').toBeGreaterThan(0)
    const doc = body.data[0]

    await gotoAs(page, fx.super, '/kb')
    await expect(page.getByText(doc.title, { exact: true }).first()).toBeVisible()
    await page.getByRole('button', { name: '查看' }).first().click()
    const detail = page.locator('.ant-drawer').filter({ hasText: doc.title })
    await expect(detail).toBeVisible()
    await expect(detail.getByText('所有者', { exact: true })).toBeVisible()
    await detail.getByRole('button', { name: /关闭|Close/ }).click()

    await page.getByRole('button', { name: '访问解释' }).first().click()
    await expect(page.getByText('对象级访问解释')).toBeVisible()
    await expect(page.getByText('LOCAL(kb_share)', { exact: true })).toBeVisible()
    await expect(page.getByText('允许', { exact: true })).toBeVisible()
  })

  test('无 oa:kb:share 时不渲染解释入口，直连接口仍被后端拒绝', async ({ page }) => {
    await gotoAs(page, fx.employee, '/kb')
    await expect(page.getByRole('button', { name: '访问解释' })).toHaveCount(0)

    const response = await backend(page, fx.employee).get('/api/v1/doc/kb/1/explain')
    expect(response.status()).toBe(403)
    expect((await response.json() as Envelope).code).toBe(3001)
  })
})
