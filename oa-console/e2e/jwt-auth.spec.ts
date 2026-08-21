import { expect, test } from '@playwright/test'

test.describe('AC-06/07 · Casdoor JWT 与浏览器 WebSocket', () => {
  test.skip(process.env.OA_JWT_E2E !== 'true', '仅在 Phase 4 JWT 全栈关卡执行')

  test('授权码 + PKCE 登录、回调、refresh token 与一次性 WS ticket', async ({ page }) => {
    test.setTimeout(60_000)
    let resolveWelcome!: (payload: string) => void
    const welcomePromise = new Promise<string>((resolve) => { resolveWelcome = resolve })
    page.on('websocket', (socket) => {
      socket.on('framereceived', (frame) => {
        const payload = String(frame.payload)
        if (payload.includes('WELCOME')) resolveWelcome(payload)
      })
    })
    const socketPromise = page.waitForEvent('websocket', {
      predicate: (socket) => socket.url().includes('/ws?ticket='),
      timeout: 20_000,
    })

    await page.goto('/iam')
    await expect(page.getByRole('button', { name: '使用 Casdoor 登录' })).toBeVisible()
    await page.getByRole('button', { name: '使用 Casdoor 登录' }).click()
    await page.waitForURL(/^http:\/\/localhost:8000\//)

    const password = page.locator('input[type="password"]:visible').first()
    const username = page.locator('input[type="text"]:visible, input:not([type]):visible').first()
    await expect(username).toBeVisible()
    await username.fill(process.env.OA_CASDOOR_USER ?? 'admin')
    await password.fill(process.env.OA_CASDOOR_PASSWORD ?? '123')
    await page.locator('button:visible').filter({ hasText: /登录|Sign in|Login/i }).first().click()

    await page.waitForURL(/\/iam$/, { timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '授权管理' })).toBeVisible()

    const socket = await socketPromise
    expect(socket.url()).toContain('?ticket=')
    expect(socket.url()).not.toContain('access_token')
    await expect(welcomePromise).resolves.toContain('WELCOME')

    const stored = await page.evaluate(() => {
      const key = Object.keys(sessionStorage).find((item) => item.startsWith('oidc.user:'))
      return key ? JSON.parse(sessionStorage.getItem(key) ?? '{}') as { refresh_token?: string } : {}
    })
    expect(stored.refresh_token).toBeTruthy()
    const refreshed = await page.request.post('http://localhost:8000/api/login/oauth/access_token', {
      form: {
        grant_type: 'refresh_token',
        client_id: 'oa-platform-local',
        refresh_token: stored.refresh_token!,
      },
    })
    expect(refreshed.ok()).toBeTruthy()
    expect((await refreshed.json()).access_token).toBeTruthy()
  })
})
