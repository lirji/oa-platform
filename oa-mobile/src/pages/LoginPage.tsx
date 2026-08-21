import { Button, Card } from 'antd-mobile'
import { useEffect } from 'react'
import { useAuth } from 'react-oidc-context'
import { Navigate, useLocation } from 'react-router-dom'
import { config } from '../config'

export default function LoginPage() {
  const auth = useAuth(); const location = useLocation()
  if (!config.authEnabled || auth.isAuthenticated) return <Navigate to="/workbench" replace />
  const returnTo = (location.state as { from?: string } | null)?.from ?? '/workbench'
  return <div className="login-page"><Card title="OA 移动办公">
    <p>登录后可办理待办、移动打卡、查找同事并阅读公告。</p>
    {auth.error && <p className="danger-text">{auth.error.message}</p>}
    <Button block color="primary" loading={auth.isLoading} onClick={() => void auth.signinRedirect({ state: { returnTo } })}>登录</Button>
  </Card></div>
}

export function CallbackPage() {
  const auth = useAuth(); const location = useLocation()
  const returnTo = ((auth.user?.state ?? location.state) as { returnTo?: string } | null)?.returnTo ?? '/workbench'
  useEffect(() => { if (auth.isAuthenticated) window.history.replaceState({}, document.title, returnTo) }, [auth.isAuthenticated, returnTo])
  if (auth.isAuthenticated) return <Navigate to={returnTo} replace />
  return <div className="center">正在完成登录…</div>
}
