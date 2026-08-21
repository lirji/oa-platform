import { defineConfig } from '@playwright/test'

const external = process.env.PLAYWRIGHT_BASE_URL
export default defineConfig({
  testDir: './e2e', timeout: 30_000, fullyParallel: false, workers: 1, reporter: 'list',
  use: {
    baseURL: external ?? 'http://127.0.0.1:5474',
    browserName: 'chromium', viewport: { width: 390, height: 844 },
    isMobile: true, hasTouch: true, deviceScaleFactor: 2,
    screenshot: 'only-on-failure',
  },
  webServer: external ? undefined : {
    command: 'pnpm exec vite --host 127.0.0.1 --port 5474', url: 'http://127.0.0.1:5474', reuseExistingServer: true,
  },
})
