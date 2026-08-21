import { defineConfig, devices } from '@playwright/test'

const externalBaseUrl = process.env.PLAYWRIGHT_BASE_URL

// e2e 跑在**专用端口 5373** 上，与 `pnpm dev` 的 5473 分开 ——
// e2e 会真的授权/撤权，与手上正在调的那个 dev server 共用会互相干扰。
// reuseExistingServer 只在 5373 上已有实例时复用（连着跑第二遍时省一次冷启）。
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  // 这些发布前 E2E 共享同一套后端与 Redis 权限纪元。授权/撤权会推进【全局】epoch，
  // 即使每个 spec 用的是不同账号，也会让其它 worker 的通讯录缓存同时失效。
  // 并行跑的结果是业务本身正确、测试却停在全量同步的不同中间页；更糟的是失败清理
  // 与下一条授权还能交错。发布关卡只有几十秒，串行换确定性是值得的。
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    // 显式 IPv4:避免 vite 默认 localhost(::1) 与探测端 127.0.0.1 的双栈错配。
    baseURL: externalBaseUrl ?? 'http://127.0.0.1:5373',
    trace: 'on-first-retry',
    // 失败时留截图，比读 trace 快
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: externalBaseUrl ? undefined : {
    command: 'pnpm exec vite --host 127.0.0.1 --port 5373',
    url: 'http://127.0.0.1:5373',
    reuseExistingServer: true,
    timeout: 60_000,
  },
})
