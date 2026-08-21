import { expect, test } from '@playwright/test'
import { actAs, backend, fx, gotoAs, type Envelope } from './helpers'

/**
 * 验收 A6（3000 节点树初始渲染 < 100 个节点）+ 成环防护。
 *
 * <p>★ 关于拖拽：**这里不模拟 HTML5 拖放**。antd Tree 的拖拽依赖一串
 * dragstart/dragover/drop 事件与内部状态，用 Playwright 合成出来既脆又慢，
 * 失败时分不清是"成环判定错了"还是"拖拽没模拟对"——那种测试的信噪比是负的。
 *
 * <p>真正要保证的两件事分别在它们该在的地方验：
 * ① 成环判定的正确性 → `src/pages/org/orgTree.test.ts` 的表驱动单测（含尾斜杠那条）；
 * ② 强行提交时服务端会拒绝、且树回到真值 → 本文件走接口验。
 */
test.describe('A6 · 组织树', () => {
  test('3000 个组织，初始只渲染两层（节点数 < 100）', async ({ page }) => {
    await gotoAs(page, fx.super, '/org')
    // ★ 排掉 aria-hidden 的那个：antd Tree 会渲染一个隐藏节点用于量高度，
    //   它既不可见也不该算进"渲染了多少节点"。
    const nodes = page.locator('.ant-tree-treenode:not([aria-hidden="true"])')
    await expect(nodes.first()).toBeVisible()

    const count = await nodes.count()
    // 缺省 maxDepth 是 Integer.MAX_VALUE，一次返回整棵树 —— 那时这里会是几千。
    expect(count, `实际渲染 ${count} 个节点（库里有 ${fx.orgs} 个组织）`).toBeLessThan(100)
    expect(count, '一个都没渲染的话这条断言毫无意义').toBeGreaterThan(0)
  })

  test('组织树接口确实带了 maxDepth（不是靠前端裁剪）', async ({ page }) => {
    await actAs(page, fx.super)
    const req = page.waitForRequest((r) => r.url().includes('/org/units/tree'))
    await page.goto('/org')
    expect((await req).url()).toContain('maxDepth=')
  })
})

test.describe('成环防护（服务端侧）', () => {
  test('把父组织移到自己的后代 → 409/2002，且树不变', async ({ page }) => {
    await gotoAs(page, fx.super, '/org')
    const b = backend(page, fx.super)

    // 取一个有子节点的组织，试着把它移进自己的孩子里
    const tree = await (await b.get('/api/v1/org/units/tree?maxDepth=2')).json() as
      Envelope<Array<{ id: number; parentId: number | null; children: Array<{ id: number }> }>>
    const parent = tree.data.find((n) => (n.children ?? []).length > 0)
    expect(parent, '树里找不到任何有子节点的组织，这个用例没意义').toBeTruthy()
    const child = parent!.children[0].id

    const before = await (await b.get(`/api/v1/org/units/${parent!.id}`)).json() as Envelope<{ parentId: number | null; path: string }>
    const resp = await page.request.put(`/api/v1/org/units/${parent!.id}/parent`, {
      headers: { 'X-OA-User': fx.super, 'Content-Type': 'application/json' },
      data: { newParentId: child },
      failOnStatusCode: false,
    })
    const body = await resp.json() as Envelope
    expect(resp.status(), '成环必须被拒').toBe(409)
    expect(body.code).toBe(2002)

    // ★ 拒绝之后**不能留下半个状态**：closure 表三步走必须在同一事务里，
    //   断到一半会留下一棵自己是自己祖先的树，之后所有数据权限查询都会错。
    const after = await (await b.get(`/api/v1/org/units/${parent!.id}`)).json() as Envelope<{ parentId: number | null; path: string }>
    expect(after.data.parentId).toBe(before.data.parentId)
    expect(after.data.path).toBe(before.data.path)
  })
})
