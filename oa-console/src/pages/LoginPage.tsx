import { Button, Card, Space, Typography } from 'antd'
import { LockOutlined, ClusterOutlined, LoginOutlined } from '@ant-design/icons'
import { useAuth } from 'react-oidc-context'
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { config } from '@oa/shared/config'

/**
 * 登录页。视觉沿用家族的左品牌 hero + 右 SSO 卡（类名前缀 wf- → oa-）。
 *
 * <p>它在 `<ProtectedRoute>` **之外**：放进去会死循环（未登录 → 跳登录 → 又被守卫拦）。
 */
export default function LoginPage() {
  const auth = useAuth()
  const nav = useNavigate()
  const loc = useLocation() as { state?: { from?: string } }
  const [sp] = useSearchParams()
  const returnTo = loc.state?.from ?? sp.get('returnTo') ?? '/workbench'

  if (config.authEnabled && auth.isAuthenticated) return <Navigate to={returnTo} replace />

  return (
    <div className="oa-login">
      <section className="oa-login-brand">
        <div className="oa-login-motif" aria-hidden />
        <div className="oa-login-badge"><ClusterOutlined style={{ fontSize: 28, color: '#fff' }} /></div>
        <Typography.Title level={2} style={{ color: '#fff', maxWidth: 500, lineHeight: 1.3, marginTop: 28 }}>
          企业级 OA 协同办公平台
        </Typography.Title>
        <p style={{ color: 'rgba(255,255,255,0.82)', fontSize: 16, maxWidth: 480 }}>
          万人级组织与权限底座：任意层级组织、一人多岗、汇报线独立于组织树，
          判权 P99 1.25 微秒，改权限秒级生效。
        </p>
        <Space wrap style={{ marginTop: 24 }}>
          {['组织人事', '权限中心', '审批流转', '考勤与公文'].map((t) => (
            <span key={t} className="oa-login-feature">{t}</span>
          ))}
        </Space>
        <div className="oa-login-footer">© OA Platform</div>
      </section>

      <section className="oa-login-auth">
        <Card className="oa-login-card" bordered={false}>
          <div className="oa-login-card-mark"><ClusterOutlined style={{ fontSize: 22, color: '#fff' }} /></div>
          <Typography.Title level={4} style={{ marginTop: 20, marginBottom: 4 }}>
            登录管理控制台
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 28 }}>
            {config.authEnabled ? '使用统一身份登录' : '开发模式：无需鉴权，直接进入'}
          </Typography.Paragraph>

          {config.authEnabled ? (
            <Button
              type="primary" block className="oa-login-sso" icon={<LoginOutlined />}
              loading={auth.isLoading}
              onClick={() => void auth.signinRedirect({ state: { returnTo } })}
            >
              使用 Casdoor 登录
            </Button>
          ) : (
            <Button type="primary" block className="oa-login-sso" onClick={() => nav(returnTo, { replace: true })}>
              进入控制台（{config.devUser}）
            </Button>
          )}

          <div className="oa-login-trust">
            <LockOutlined /> OIDC 授权码 + PKCE · Casdoor 单点登录
          </div>
        </Card>
      </section>
    </div>
  )
}
