import { useEffect, useMemo, useState } from 'react'
import { Avatar, Alert, Breadcrumb, Button, Drawer, Dropdown, Grid, Layout, Menu, Space, Tag, Tooltip } from 'antd'
import {
  ClusterOutlined, LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined, MenuOutlined,
  SafetyCertificateOutlined, UserOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { config } from '@oa/shared/config'
import type { MenuNode } from '@oa/shared/perm/types'
import { useAuthStore } from '../../store/authStore'
import { usePerm } from '../../auth/usePerm'
import { colors } from '../../theme/colors'
import { menuIcon } from './menuIcons'

/** 后端 menus → antd Menu items。层级与排序都来自后端。 */
function toItems(nodes: readonly MenuNode[]): NonNullable<Parameters<typeof Menu>[0]['items']> {
  return nodes.map((n) => ({
    key: n.route ?? n.code,
    icon: menuIcon(n.icon),
    label: n.name,
    children: n.children?.length ? toItems(n.children) : undefined,
  }))
}

export default function AppLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const auth = useAuth()
  const username = useAuthStore((s) => s.username)
  const perm = usePerm()

  const screens = Grid.useBreakpoint()
  const isMobile = !screens.lg
  // ★ C 档（1280–1439，1366×768 落这里）默认折叠侧栏：
  //   1366 宽下展开的 224px 侧栏白吃 152px，而那一档的可用高度本就只剩 ~620px。
  const narrowDesktop = !screens.xxl && !isMobile
  const [collapsed, setCollapsed] = useState(narrowDesktop)
  const [drawerOpen, setDrawerOpen] = useState(false)
  useEffect(() => { setCollapsed(narrowDesktop) }, [narrowDesktop])

  const items = useMemo(() => toItems(perm.menus ?? []), [perm.menus])
  const current = useMemo(
    () => flatten(perm.menus ?? []).find(
      (n) => n.route && (location.pathname === n.route || location.pathname.startsWith(n.route + '/')),
    ),
    [perm.menus, location.pathname],
  )

  const menu = (afterClick?: () => void) => (
    <Menu
      mode="inline"
      selectedKeys={[current?.route ?? location.pathname]}
      items={items}
      onClick={(e) => { navigate(e.key); afterClick?.() }}
      style={{ borderInlineEnd: 0 }}
    />
  )

  const brand = (
    <div className="brand">
      <ClusterOutlined style={{ color: colors.primary }} />
      {!collapsed && 'OA 管理控制台'}
    </div>
  )

  const userArea = (
    <Space size={8}>
      {/* ★ 提权中的显式提示 + 倒计时。数据源是 /iam/elevations/mine 的 remainingMs ——
          没有它就只能拿快照缓存 TTL 凑数，显示一个到点后会误报"提权已过期"的假数字。 */}
      {perm.elevations.length > 0 && (
        <Tooltip title={perm.elevations.map((e) => `${e.roleName}：${e.reason ?? '无说明'}`).join('；')}>
          <Tag icon={<SafetyCertificateOutlined />} color="orange">
            提权中 · 剩 {Math.max(1, Math.round(perm.elevationRemainingMs / 60000))} 分钟
          </Tag>
        </Tooltip>
      )}
      {config.authEnabled ? (
        <Dropdown
          menu={{
            items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录' }],
            onClick: () => void auth.signoutRedirect(),
          }}
        >
          <Button type="text" style={{ height: 'auto', paddingBlock: 4 }}>
            <Space><Avatar size="small" icon={<UserOutlined />} />{username ?? '未登录'}</Space>
          </Button>
        </Dropdown>
      ) : (
        <Tag color="orange">开发模式 · {config.devUser}</Tag>
      )}
    </Space>
  )

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {!isMobile && (
        <Layout.Sider
          theme="light" width={224} collapsedWidth={72} collapsible collapsed={collapsed} trigger={null}
          style={{ borderInlineEnd: `1px solid ${colors.border}` }}
        >
          {brand}
          {menu()}
        </Layout.Sider>
      )}

      <Layout>
        <Layout.Header style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          borderBottom: `1px solid ${colors.border}`,
        }}>
          <Space>
            <Button
              type="text"
              aria-label={isMobile ? '打开菜单' : collapsed ? '展开菜单' : '收起菜单'}
              icon={isMobile ? <MenuOutlined /> : collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => (isMobile ? setDrawerOpen(true) : setCollapsed(!collapsed))}
            />
            <Breadcrumb items={[{ title: 'OA' }, { title: current?.name ?? '' }]} />
          </Space>
          {userArea}
        </Layout.Header>

        <Layout.Content>
          <div className="app-content">
            {/* ★ 代理身份横幅。后端 CompleteTask 支持 onBehalfOf，界面不提示的话
                用户会"操作到别人头上而不自知"——而审计里记的是他的名字。 */}
            {perm.delegators.length > 0 && (
              <Alert
                type="info" showIcon style={{ marginBottom: 16 }}
                message={`你正代理 ${perm.delegators.join('、')} 的待办`}
                description="代理办理会在审批轨迹上留下 on_behalf_of 记录。代理不会扩大你自己的权限。"
              />
            )}
            <Outlet />
          </div>
        </Layout.Content>
      </Layout>

      <Drawer
        placement="left" width={224} open={isMobile && drawerOpen}
        onClose={() => setDrawerOpen(false)} styles={{ body: { padding: 0 } }}
        title={<Space><ClusterOutlined style={{ color: colors.primary }} />OA 管理控制台</Space>}
      >
        {menu(() => setDrawerOpen(false))}
      </Drawer>
    </Layout>
  )
}

function flatten(nodes: readonly MenuNode[]): MenuNode[] {
  return nodes.flatMap((n) => [n, ...flatten(n.children ?? [])])
}
