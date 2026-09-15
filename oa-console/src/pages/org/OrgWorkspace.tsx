import { Segmented } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { PERM } from '@oa/shared/perm/codes'
import { PageHeader } from '../../components/layout/PageHeader'
import { ScopeBanner } from '../../components/common/ScopeHint'
import { usePerm } from '../../auth/usePerm'

/**
 * 组织人事作业面：侧栏只有「组织」一项，员工/通讯录用页签进，路径仍是
 * `/org`、`/org/employees`、`/org/directory`（e2e 与书签都不改）。
 */
export default function OrgWorkspace() {
  const loc = useLocation()
  const nav = useNavigate()
  const perm = usePerm()

  const active = loc.pathname.startsWith('/org/directory')
    ? '/org/directory'
    : loc.pathname.startsWith('/org/employees')
      ? '/org/employees'
      : '/org'

  const items = [
    perm.has(PERM.ORG_VIEW) ? { key: '/org', label: '组织树' } : null,
    perm.has(PERM.EMPLOYEE_VIEW) ? { key: '/org/employees', label: '员工' } : null,
    perm.has(PERM.EMPLOYEE_VIEW) ? { key: '/org/directory', label: '通讯录' } : null,
  ].filter(Boolean) as { key: string; label: string }[]

  const tab = items.some((i) => i.key === active) ? active : items[0]?.key

  return (
    <>
      <PageHeader
        title="组织人事"
        description="组织树默认只加载两层；展开部门才会请求下级组。员工与通讯录按你的可见范围过滤。"
      />
      <ScopeBanner module="org" />
      {items.length > 0 && tab && (
        <div style={{ marginBottom: 16 }}>
          <Segmented
            value={tab}
            onChange={(key) => { void nav(String(key)) }}
            options={items.map((i) => ({ label: i.label, value: i.key }))}
          />
        </div>
      )}
      <Outlet />
    </>
  )
}
