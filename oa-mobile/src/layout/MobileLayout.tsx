import { Badge, NavBar, TabBar } from 'antd-mobile'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import type { ApiResult } from '../api/types'
import { usePermissions } from '../permissions'

const tabs = [
  { key: '/workbench', title: '待办', icon: <span aria-hidden>✓</span> },
  { key: '/attendance', title: '打卡', icon: <span aria-hidden>◷</span> },
  { key: '/directory', title: '通讯录', icon: <span aria-hidden>◎</span> },
  { key: '/announcements', title: '公告', icon: <span aria-hidden>◇</span> },
]

export default function MobileLayout() {
  const location = useLocation(); const navigate = useNavigate(); const { perms, username } = usePermissions()
  const count = useQuery({
    queryKey: ['todo-count'], enabled: perms.has('oa:flow:todo:view'), refetchInterval: 30_000,
    queryFn: async () => (await api.get<ApiResult<{ pending: number }>>('/api/v1/flow/todos/count')).data.data.pending,
  })
  const active = tabs.find((item) => location.pathname.startsWith(item.key))?.key ?? '/workbench'
  const title = tabs.find((item) => item.key === active)?.title ?? 'OA 移动端'
  return <div className="app-shell">
    <header><NavBar back={null} right={<span className="account-name">{username ?? ''}</span>}>{title}</NavBar></header>
    <main><Outlet /></main>
    <footer><TabBar activeKey={active} onChange={navigate} safeArea>
      {tabs.map((item) => <TabBar.Item key={item.key} icon={item.icon} title={item.title}
        badge={item.key === '/workbench' && count.data ? Badge.dot : undefined} />)}
    </TabBar></footer>
  </div>
}
