import { useEffect } from 'react'
import { Result, Spin } from 'antd'
import { useAuth } from 'react-oidc-context'
import { useNavigate } from 'react-router-dom'

/** OIDC 回调。按 `state.returnTo` 回跳原深链 —— 否则用户点的深链会在登录后丢掉。 */
export default function CallbackPage() {
  const auth = useAuth()
  const nav = useNavigate()

  useEffect(() => {
    if (auth.isLoading || auth.activeNavigator) return
    if (auth.isAuthenticated) {
      const to = (auth.user?.state as { returnTo?: string } | undefined)?.returnTo ?? '/workbench'
      nav(to, { replace: true })
    }
  }, [auth.isLoading, auth.activeNavigator, auth.isAuthenticated, auth.user, nav])

  if (auth.error) {
    return <Result status="error" title="登录回调失败" subTitle={auth.error.message} />
  }
  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Spin size="large" tip="正在完成登录..." />
    </div>
  )
}
