import { defineConfig } from 'vitest/config'
import { loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  return {
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      port: 5474,
      proxy: {
        '/api/v1/notify': { target: env.VITE_NOTIFY_TARGET || 'http://localhost:8401', changeOrigin: true },
        '/api/v1/announcements': { target: env.VITE_NOTIFY_TARGET || 'http://localhost:8401', changeOrigin: true },
        '/api/v1/file': { target: env.VITE_FILE_TARGET || 'http://localhost:8402', changeOrigin: true },
        '/api/v1/job': { target: env.VITE_JOB_TARGET || 'http://localhost:8403', changeOrigin: true },
        '/api/v1': { target: env.VITE_API_TARGET || 'http://localhost:8400', changeOrigin: true },
      },
    },
    build: {
      rollupOptions: { output: { manualChunks: { react: ['react', 'react-dom', 'react-router-dom'], oidc: ['oidc-client-ts', 'react-oidc-context'] } } },
      chunkSizeWarningLimit: 800,
    },
    test: { environment: 'jsdom', globals: true, setupFiles: ['./src/test/setup.ts'], include: ['src/**/*.test.{ts,tsx}'] },
  }
})
