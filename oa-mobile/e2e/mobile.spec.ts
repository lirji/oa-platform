import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('oa.devUser', 'seed-user-1'))
})

test('四个核心场景在窄屏可用', async ({ page }) => {
  await page.goto('/workbench')
  await expect(page.getByTestId('todos-page').or(page.getByText('当前没有待办'))).toBeVisible()
  await expect(page.locator('body')).toHaveCSS('overflow-x', /^(visible|auto)$/)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await page.getByText('打卡', { exact: true }).click()
  await expect(page.getByTestId('attendance-page')).toBeVisible()
  const punched = page.waitForResponse((response) => response.url().includes('/attendance/punch'))
  await page.getByRole('button', { name: '上班打卡' }).click()
  expect((await punched).ok()).toBe(true)
  await expect(page.locator('.adm-toast-main')).toBeVisible()

  await page.getByText('通讯录', { exact: true }).click()
  await expect(page.getByTestId('directory-page')).toBeVisible()
  await page.getByPlaceholder('搜索姓名、工号、手机号').fill('员工')
  await expect(page.locator('.adm-list-item').first()).toBeVisible()

  await page.getByText('公告', { exact: true }).click()
  await expect(page.getByTestId('announcements-page').or(page.getByText('暂时没有公告'))).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await page.setViewportSize({ width: 320, height: 640 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('可办理指定待办并阅读指定公告', async ({ page }) => {
  const taskTitle = process.env.OA_MOBILE_TODO_TITLE
  const taskId = process.env.OA_MOBILE_TODO_TASK
  const announcementTitle = process.env.OA_MOBILE_ANNOUNCEMENT_TITLE
  test.skip(!taskTitle || !taskId || !announcementTitle, 'phase5 smoke 会注入真实夹具')

  await page.goto('/workbench')
  const todo = page.locator(`[data-task-id="${taskId}"]`).filter({ hasText: taskTitle! })
  await expect(todo).toBeVisible()
  await todo.getByRole('button', { name: '同意' }).click()
  const completed = page.waitForResponse((response) => response.url().includes(`/flow/todos/${taskId}/complete`))
  await page.getByRole('button', { name: '确定' }).click()
  expect((await completed).ok()).toBe(true)
  await expect(page.getByText('办理成功')).toBeVisible()
  await expect(todo).toBeHidden()

  await page.getByText('公告', { exact: true }).click()
  const announcement = page.locator('.adm-card').filter({ hasText: announcementTitle! })
  await expect(announcement).toBeVisible()
  await announcement.getByRole('button', { name: '标为已读' }).click()
  await expect(announcement.getByText('已读', { exact: true })).toBeVisible()
})
