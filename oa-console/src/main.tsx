import React from 'react'
import ReactDOM from 'react-dom/client'
import { ConfigProvider, App as AntdApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { router } from './router/routes'
import AppAuthProvider from './auth/AppAuthProvider'
import PermBridge from './auth/PermBridge'
import { appTheme } from './theme/theme'
import './styles/global.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { refetchOnWindowFocus: false, staleTime: 30_000, retry: 1 },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN} theme={appTheme}>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <AppAuthProvider>
            {/* 权限装载桥：把 /me/permissions 同步进 permStore，并订阅 X-OA-Perm-Version。
                必须在 RouterProvider【之前】—— 路由守卫要读它。 */}
            <PermBridge />
            <RouterProvider router={router} />
          </AppAuthProvider>
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  </React.StrictMode>,
)
