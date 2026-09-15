// defineConfig 从 vitest/config 取（才带 test 段的类型），
// 但 loadEnv 只在 vite 里导出 —— 从 vitest/config 拿会报
// "does not provide an export named 'loadEnv'"，且只在构建时才炸。
import { defineConfig } from 'vitest/config'
import { loadEnv, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

// 门户跨域探测专用健康端点；不走 SPA 回退。
function healthzPlugin(): Plugin {
  const respond = (res: { setHeader(name: string, value: string): void; end(body?: string): void; statusCode: number }) => {
    res.setHeader('Access-Control-Allow-Origin', '*')
    res.setHeader('Cache-Control', 'no-store')
    res.statusCode = 204
    res.end()
  }
  return {
    name: 'oa-console-healthz',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url?.split('?')[0] !== '/healthz') return next()
        respond(res)
      })
    },
    configurePreviewServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url?.split('?')[0] !== '/healthz') return next()
        respond(res)
      })
    },
  }
}

// loadEnv(mode, '.', '') —— 第三个参数留空表示不按 VITE_ 前缀过滤，
// 这样 dev-only 的 *_TARGET 也能读到（它们只用于 proxy，不会进产物）。
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')

  // ★ oa-console 与两个前辈最大的结构性差异：它们是【单上游】，这里是【四上游】。
  //   路径前缀天然不冲突，但匹配是按声明顺序的 —— 具体前缀必须排在 /api/v1 之前，
  //   否则 notify/file/job 的请求会全部被打到 8400 上，返回 404 而不是报错。
  const notify = env.VITE_NOTIFY_TARGET || 'http://localhost:8401'
  const file = env.VITE_FILE_TARGET || 'http://localhost:8402'
  const job = env.VITE_JOB_TARGET || 'http://localhost:8403'
  const app = env.VITE_API_TARGET || 'http://localhost:8400'

  return {
    plugins: [react(), healthzPlugin()],
    resolve: {
      // 可迁移税：业务代码一律 import '@oa/shared/xxx'，
      // Phase 6 抽 workspace 时只改这里的指向，业务代码一行不动。
      alias: { '@oa/shared': path.resolve(__dirname, 'src/shared') },
    },
    server: {
      // ★ 显式绑 IPv4：vite 默认绑 `localhost`，在 macOS 上解析成 ::1，
      //   而 curl/Playwright 打 127.0.0.1 会 connection refused ——
      //   表现是"dev server 明明说 ready 了却连不上"。
      //   playwright.config.ts 的 baseURL 也是 127.0.0.1，两边必须一致。
      host: '127.0.0.1',
      // 能力门户入口默认 8404；未设 OA_CONSOLE_PORT 时仍用本机 Vite 5473。
      port: Number(process.env.OA_CONSOLE_PORT || process.env.OA_UI_PORT || 5473),
      proxy: {
        // ⚠️ 顺序敏感：具体在前、笼统在后
        '/api/v1/notify': { target: notify, changeOrigin: true },
        '/api/v1/announcements': { target: notify, changeOrigin: true },
        '/api/v1/file': { target: file, changeOrigin: true },
        '/api/v1/job': { target: job, changeOrigin: true },
        '/api/v1': { target: app, changeOrigin: true },
        // 长连也走代理，避免浏览器跨源；ws:true 是必须的，漏了会 400
        '/ws': { target: notify, changeOrigin: true, ws: true },
        // Casdoor(:8000) 【不代理】—— authority 必须直连，代理会破坏 issuer 一致性。
        // 两个前辈的注释都写死了这条。
      },
    },
    build: {
      // 分包 ≠ 不进首屏。真正让重资产不进首屏的是路由级 React.lazy；
      // manualChunks 只决定"进来的时候是哪一块"。
      rollupOptions: {
        output: {
          manualChunks: {
            react: ['react', 'react-dom', 'react-router-dom'],
            // 试：不手工分 antd，让 rollup 按实际引用切 —— Table/Tree/Timeline
            // 只被具体页面用，理论上应该落进路由块而不是首屏
            icons: ['@ant-design/icons'],
            query: ['@tanstack/react-query'],
            oidc: ['oidc-client-ts', 'react-oidc-context'],
            virtual: ['@tanstack/react-virtual'],
            idb: ['idb'],
          },
        },
      },
      chunkSizeWarningLimit: 1200,
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      // 必须限死 src/**，否则 vitest 会去收集 e2e/ 下的 Playwright 用例然后炸掉
      include: ['src/**/*.{test,spec}.{ts,tsx}'],
    },
  }
})
