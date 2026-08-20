import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Result, Skeleton } from 'antd'
import { usePerm } from './usePerm'

interface PermRouteProps {
  code: string | string[]
  any?: boolean
  children?: ReactNode
}

/**
 * 路由级授权守卫。`<ProtectedRoute>`（认证）的下一层。
 *
 * <p>★ **必须包在 `<Suspense>` 外层**：`<PermRoute><Suspense><Lazy/></Suspense></PermRoute>`。
 * 写反了会先下载几百 KB 的 chunk 再告诉用户 403 —— 既白下载，又泄露了"这个页面存在"。
 */
export default function PermRoute({ code, any = false, children }: PermRouteProps) {
  const perm = usePerm()
  const loc = useLocation()
  const codes = Array.isArray(code) ? code : [code]

  // ★ 加载中不能渲染 403：权限还没到就判"无权限"，用户会在每次刷新时先看到一下 403 再跳走。
  if (!perm.loaded) return <Skeleton active paragraph={{ rows: 6 }} style={{ padding: 24 }} />

  if (perm.anonymous) {
    return <Navigate to={`/login?returnTo=${encodeURIComponent(loc.pathname + loc.search)}`} replace />
  }

  const allowed = any ? perm.hasAny(codes) : perm.hasAll(codes)
  if (!allowed) {
    return (
      <Result
        status="403"
        title="无访问权限"
        subTitle={`该页面需要：${codes.join(any ? ' 或 ' : ' 且 ')}`}
      />
    )
  }
  return <>{children ?? <Outlet />}</>
}
