import type { ReactNode } from 'react'
import { useAuth } from 'react-oidc-context'
import { Navigate, useLocation } from 'react-router-dom'
import { Button, Result, Spin } from 'antd'
import { config } from '@oa/shared/config'

const centered: React.CSSProperties = {
  minHeight: '60vh',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
}

/**
 * **认证**守卫（只回答"你是谁"）。授权由 `<PermRoute>` 负责。
 *
 * <p>★ 与 workflow-console 的原版差一处：**去掉了 `canRead(authorities)` 那段**。
 * 原版判的是 Casdoor 组名 `PHARMACIST` / `ADMIN` —— OA 里没有这两个组，
 * 照抄过来会让每个人都停在 403（FINAL_PLAN 把它列为"克隆 console 的已知坑"）。
 * OA 的"能不能进这一页"由 `<PermRoute code="oa:menu:xxx">` 判，数据源是后端 permCodes。
 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const location = useLocation()

  // 鉴权分期：DEV 直接放行，用 X-OA-User 直连后端联调。
  if (!config.authEnabled) return <>{children}</>

  if (auth.isLoading || auth.activeNavigator) {
    return <div style={centered}><Spin size="large" tip="加载中..." /></div>
  }

  if (auth.error) {
    return (
      <Result
        status="error"
        title="登录失败"
        subTitle={auth.error.message}
        extra={<Button type="primary" onClick={() => void auth.signinRedirect()}>重试登录</Button>}
      />
    )
  }

  if (!auth.isAuthenticated) {
    // 带上原深链，登录后由 CallbackPage 回跳
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }

  return <>{children}</>
}
