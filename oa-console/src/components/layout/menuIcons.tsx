import type { ReactNode } from 'react'
import {
  ApartmentOutlined, AppstoreOutlined, BarChartOutlined, BellOutlined, BookOutlined,
  ClockCircleOutlined, DashboardOutlined, FileTextOutlined, SafetyOutlined, ShopOutlined,
} from '@ant-design/icons'

/**
 * 后端下发的 icon 名 → antd 图标组件。
 *
 * <p>这是前后端之间**唯一**需要前端维护的菜单相关映射：后端给不了 React 组件，
 * 只能给名字。路由、层级、排序、可见性全部由后端 `menus` 决定 ——
 * 前端**不再维护第二份菜单定义**（那会与后端的权限点目录漂移）。
 *
 * <p>映射不到时返回一个中性图标而不是崩溃：后端加了新菜单、前端还没补图标，
 * 结果应该是"图标难看"而不是"整个侧边栏白屏"。
 */
const ICONS: Record<string, ReactNode> = {
  DashboardOutlined: <DashboardOutlined />,
  ApartmentOutlined: <ApartmentOutlined />,
  ClockCircleOutlined: <ClockCircleOutlined />,
  FileTextOutlined: <FileTextOutlined />,
  BookOutlined: <BookOutlined />,
  ShopOutlined: <ShopOutlined />,
  BellOutlined: <BellOutlined />,
  BarChartOutlined: <BarChartOutlined />,
  SafetyOutlined: <SafetyOutlined />,
}

export function menuIcon(name: string | null | undefined): ReactNode {
  return (name && ICONS[name]) || <AppstoreOutlined />
}
