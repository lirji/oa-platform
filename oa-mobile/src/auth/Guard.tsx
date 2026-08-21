import type { ReactNode } from 'react'
import { DotLoading, ErrorBlock } from 'antd-mobile'
import { useAuth } from 'react-oidc-context'
import { Navigate, useLocation } from 'react-router-dom'
import { config } from '../config'

export default function Guard({ children }: { children: ReactNode }) {
  const auth = useAuth(); const loc = useLocation()
  if (!config.authEnabled) return <>{children}</>
  if (auth.isLoading || auth.activeNavigator) return <div className="center"><DotLoading color="primary" /></div>
  if (auth.error) return <ErrorBlock status="default" title="登录失败" description={auth.error.message} />
  if (!auth.isAuthenticated) return <Navigate to="/login" replace state={{ from: loc.pathname + loc.search }} />
  return <>{children}</>
}
