import { expect, test } from '@playwright/test'
import { backend, fx, gotoAs, type Envelope } from './helpers'

/**
 * A2 · 权限沙盘必须解释完整来源链。
 *
 * 只断言页面“有几行”不够：字段映射一旦把 `via` / `rolePath` / `grantType` /
 * `validTo` 中任意一个写错，沙盘仍会渲染，却已经失去解释价值。这里先用真实 API
 * 证明夹具确有组织继承与临时授权，再逐字段验证界面。
 */
test.describe('A2 · 权限来源链', () => {
  test('组织授权显示来源、继承路径、范围与授权形态', async ({ page }) => {
    const b = backend(page, fx.super)
    const response = await b.get(`/api/v1/iam/admin/why?userId=${fx.employee}&permCode=oa:employee:view`)
    const body = await response.json() as Envelope<{
      sources: Array<{ subjectType: string; via: string; rolePath: string; scopeType: string; grantType: string }>
    }>
    const inherited = body.data.sources.find((source) => source.subjectType === 'ORG_UNIT')
    expect(inherited, '夹具必须含 ORG_UNIT 来源，否则这条验收没有意义').toBeTruthy()

    await gotoAs(page, fx.super, `/iam/sandbox?userId=${fx.employee}&permCode=oa:employee:view`)
    await expect(page.getByText(inherited!.via, { exact: true })).toBeVisible()
    await expect(page.getByText(new RegExp(inherited!.rolePath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))).toBeVisible()
    await expect(page.getByText(inherited!.scopeType, { exact: true }).first()).toBeVisible()
    await expect(page.getByText(inherited!.grantType, { exact: true }).first()).toBeVisible()
  })

  test('临时授权明确显示 TEMPORARY 与到期时间', async ({ page }) => {
    const b = backend(page, fx.super)
    const response = await b.get(`/api/v1/iam/admin/why?userId=${fx.manager}&permCode=oa:employee:view`)
    const body = await response.json() as Envelope<{
      sources: Array<{ grantType: string; validTo: string | null; via: string }>
    }>
    const temporary = body.data.sources.find((source) => source.grantType === 'TEMPORARY' && source.validTo)
    expect(temporary, '夹具必须含仍有效的临时授权').toBeTruthy()

    await gotoAs(page, fx.super, `/iam/sandbox?userId=${fx.manager}&permCode=oa:employee:view`)
    await expect(page.getByText('TEMPORARY', { exact: true }).first()).toBeVisible()
    await expect(page.getByText(/到期\s+\d{4}/).first()).toBeVisible()
    await expect(page.getByText(temporary!.via, { exact: true }).first()).toBeVisible()
  })
})
