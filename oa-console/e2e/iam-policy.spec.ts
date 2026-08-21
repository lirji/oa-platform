import { expect, test } from '@playwright/test'
import { fx, gotoAs } from './helpers'

test.describe('USER_GROUP / ABAC 管理面', () => {
  test('管理员可进入三类策略视图', async ({ page }) => {
    await gotoAs(page, fx.super, '/iam')
    await expect(page.getByRole('heading', { name: '权限策略' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '主体授权' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '用户组', exact: true })).toBeVisible()
    await expect(page.getByRole('tab', { name: 'ABAC 条件' })).toBeVisible()

    const groups = page.waitForResponse((r) => r.url().includes('/api/v1/iam/groups') && r.status() === 200)
    await page.getByRole('tab', { name: '用户组', exact: true }).click()
    await groups
    await expect(page.getByRole('button', { name: '新建用户组' })).toBeVisible()

    const conditions = page.waitForResponse((r) => r.url().includes('/api/v1/iam/abac/conditions') && r.status() === 200)
    await page.getByRole('tab', { name: 'ABAC 条件' }).click()
    await conditions
    await expect(page.getByText('ABAC 默认关闭', { exact: false })).toBeVisible()
    await expect(page.getByRole('button', { name: '新建条件' })).toBeVisible()

  })

  test('普通员工不能进入权限策略页', async ({ page }) => {
    await gotoAs(page, fx.employee, '/iam')
    await expect(page.getByText('无访问权限')).toBeVisible()
    await expect(page.getByRole('tab', { name: '用户组', exact: true })).toHaveCount(0)
  })

  test('窄屏仍可操作策略页签', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await gotoAs(page, fx.super, '/iam')
    await page.getByRole('tab', { name: '用户组', exact: true }).click()
    await expect(page.getByRole('button', { name: '新建用户组' })).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(1)
  })
})
