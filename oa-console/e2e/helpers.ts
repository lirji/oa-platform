import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Page } from '@playwright/test'

const HERE = dirname(fileURLToPath(import.meta.url))

export interface Fixtures {
  /** SUPER_ADMIN / ALL */
  super: string
  /** HR_ADMIN / ALL */
  hr: string
  /** DEPT_MANAGER / ORG_AND_SUB（另有一条 TEMPORARY 授权，沙盘要用） */
  manager: string
  /** EMPLOYEE / SELF */
  employee: string
  /** 没有任何授权 */
  noPerm: string
  /** 被"含下级"授权的那个组织 —— 沙盘来源链里的"部门继承"就来自它 */
  inheritedOrgId: number
  orgs: number
  employees: number
}

/** 由 `deploy/scripts/seed-console-fixture.sh` 生成。 */
export const fx: Fixtures = JSON.parse(readFileSync(resolve(HERE, 'fixtures.json'), 'utf8'))

/**
 * 让这个页面以某个账号的身份运行。**必须在第一次 `goto` 之前调用。**
 *
 * <p>走 `localStorage['oa.devUser']`（`config.devUserOverride()`）——
 * `VITE_DEV_USER` 是启动时固定的，没有这个覆写就得为五个账号各起一个 dev server。
 * 覆写在 JWT 模式下恒为 null，不会变成一道后门。
 */
export async function actAs(page: Page, user: string): Promise<void> {
  await page.addInitScript((u) => window.localStorage.setItem('oa.devUser', u), user)
}

/**
 * 绕过前端直接打后端（同源，走 vite proxy）。
 *
 * <p>★ A3 的核心手法：**同一个浏览器上下文**里发请求，证明"前端把按钮藏了"
 * 与"后端会拒绝"是两件独立成立的事。`page.request` 不带页面的 localStorage，
 * 所以身份要显式给 —— 这恰好更忠实：攻击者本来就不会带你的前端状态。
 */
export function backend(page: Page, user: string) {
  const headers = { 'X-OA-User': user, 'Content-Type': 'application/json' }
  return {
    get: (path: string) => page.request.get(path, { headers, failOnStatusCode: false }),
    post: (path: string, data?: unknown) =>
      page.request.post(path, { headers, data: data ?? {}, failOnStatusCode: false }),
    del: (path: string) => page.request.delete(path, { headers, failOnStatusCode: false }),
  }
}

/** 统一响应信封 `{ code, message, data }`。 */
export interface Envelope<T = unknown> { code: number; message: string; data: T }

/**
 * 以某个身份打开一个页面，并等到权限装载完毕。
 *
 * <p>★ 监听必须挂在 `goto` **之前**。写成 `await page.goto(); await waitForResponse()`
 * 的话，只要 `/me/permissions` 在 load 事件之前就回来了，监听就永远等不到 ——
 * 而这取决于机器快慢和是否命中缓存，于是得到一条"平时绿、偶尔挂满 30 秒"的测试。
 * （dev server 慢的时候它一直是绿的，正是这种侥幸最难发现。）
 */
export async function gotoAs(page: Page, user: string, path: string): Promise<void> {
  await actAs(page, user)
  const perms = page.waitForResponse((r) => r.url().includes('/me/permissions') && r.status() === 200)
  await page.goto(path)
  await perms
}
