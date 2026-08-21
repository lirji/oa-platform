import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import MobileAuthProvider from './auth/AuthProvider'
import { router } from './router'
import 'antd-mobile/es/global'
import './styles.css'

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } } })
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode>
  <MobileAuthProvider><QueryClientProvider client={queryClient}><RouterProvider router={router} /></QueryClientProvider></MobileAuthProvider>
</React.StrictMode>)
