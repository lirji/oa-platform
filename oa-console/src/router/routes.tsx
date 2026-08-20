import { lazy, Suspense, type ReactNode } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { Spin } from 'antd'
import ProtectedRoute from '../auth/ProtectedRoute'
import PermRoute from '../auth/PermRoute'
import LoginPage from '../pages/LoginPage'
import CallbackPage from '../pages/CallbackPage'
import ForbiddenPage from '../pages/ForbiddenPage'

/**
 * ★ 应用壳（AppLayout + 工作台）也懒加载。
 *
 * <p>原本的想法是"工作台是首页，懒加载只多一次往返"。但实测发现：
 * 壳里的 Layout/Menu/Drawer/Table/Tabs/Modal 会把 antd 的大半拖进首屏 chunk，
 * **首屏 414 KB gzip，硬预算是 300 KB**。
 *
 * <p>改成懒加载后，未登录访客只下载登录页需要的那点东西（Card/Button/Typography），
 * 壳在登录之后才加载 —— 那时用户已经在等页面切换，多一次往返感知不到。
 * 这也让"首屏预算"有了一个诚实的口径：**第一次访问的人实际下载了多少**。
 */
const AppLayout = lazy(() => import('../components/layout/AppLayout'))
const WorkbenchPage = lazy(() => import('../pages/workbench/WorkbenchPage'))

// ★ 路由级 lazy 才是"不进首屏"的保证；vite 的 manualChunks 只决定"进来时是哪一块"。
//   chunk 边界刻意 = MENU code 边界，这样"菜单可见性"与"代码分割"是同一条线，最好解释。
const OrgTreePage = lazy(() => import('../pages/org/OrgTreePage'))
const EmployeesPage = lazy(() => import('../pages/org/EmployeesPage'))
const DirectoryPage = lazy(() => import('../pages/org/DirectoryPage'))
const GrantsPage = lazy(() => import('../pages/iam/GrantsPage'))
const SandboxPage = lazy(() => import('../pages/iam/SandboxPage'))
const ReportPage = lazy(() => import('../pages/report/ReportPage'))
const AuditPage = lazy(() => import('../pages/report/AuditPage'))

const loading = (
  <div style={{ minHeight: '60vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
    <Spin size="large" />
  </div>
)

/**
 * ★ 守卫必须在 Suspense **外层**。
 * 写反了会先下载几百 KB 的 chunk 再告诉用户 403 —— 既白下载，又泄露了"这个页面存在"。
 */
const guarded = (code: string, node: ReactNode) => (
  <PermRoute code={code}>
    <Suspense fallback={loading}>{node}</Suspense>
  </PermRoute>
)

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/callback', element: <CallbackPage /> },
  { path: '/403', element: <ForbiddenPage /> },
  {
    path: '/',
    element: <ProtectedRoute><Suspense fallback={loading}><AppLayout /></Suspense></ProtectedRoute>,
    children: [
      { index: true, element: <Suspense fallback={loading}><WorkbenchPage /></Suspense> },
      { path: 'workbench', element: <Suspense fallback={loading}><WorkbenchPage /></Suspense> },

      { path: 'org', element: guarded('oa:org:view', <OrgTreePage />) },
      { path: 'org/employees', element: guarded('oa:employee:view', <EmployeesPage />) },
      { path: 'org/directory', element: guarded('oa:employee:view', <DirectoryPage />) },

      { path: 'iam', element: guarded('oa:iam:view', <GrantsPage />) },
      { path: 'iam/sandbox', element: guarded('oa:iam:admin', <SandboxPage />) },

      { path: 'report', element: guarded('oa:report:view', <ReportPage />) },
      { path: 'report/audit', element: guarded('oa:report:view', <AuditPage />) },
    ],
  },
])
