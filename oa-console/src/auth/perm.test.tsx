import { Button } from 'antd'
import { Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import { usePermStore } from '../store/permStore'
import type { MyPermissions } from '@oa/shared/perm/types'
import Can from './Can'

/**
 * ★ antd 会给**恰好两个汉字**的按钮文案中间插一个空格（`insertSpace`），
 * 于是可访问名是 "撤 权" 而不是 "撤权"，`getByRole({name:'撤权'})` 直接找不到。
 * 一律用正则匹配，别为了绕开它去改文案 —— 改文案是让测试决定 UI，本末倒置。
 */
const btn = (label: string) => new RegExp(label.split('').join('\\s*'))
import PermRoute from './PermRoute'
import { usePerm } from './usePerm'

const base: MyPermissions = {
  userId: 'u-1', username: 'alice', employeeId: 1, primaryOrgId: 1, primaryOrgPath: '/1/',
  version: 1, permCodes: [], menus: [], dataScope: 'SELF', scopePrefixes: ['/1/'],
  delegators: [], elevatedCodes: [], moduleScope: {},
}

function login(codes: string[], extra: Partial<MyPermissions> = {}) {
  usePermStore.getState().reset()
  usePermStore.getState().applyPermissions({ ...base, ...extra, permCodes: codes })
}

afterEach(() => usePermStore.getState().reset())

// ─────────────────────────────────────────────── <Can>

describe('<Can>', () => {
  it('有权限就渲染', () => {
    login(['oa:iam:revoke'])
    renderWithProviders(<Can code="oa:iam:revoke"><Button>撤权</Button></Can>)
    expect(screen.getByRole('button', { name: btn('撤权') })).toBeInTheDocument()
  })

  it('没权限默认整个藏掉', () => {
    login(['oa:org:view'])
    renderWithProviders(<Can code="oa:iam:revoke"><Button>撤权</Button></Can>)
    expect(screen.queryByRole('button', { name: btn('撤权') })).not.toBeInTheDocument()
  })

  it('★ 未知 code 一律判拒（不是"没见过就放行"）', () => {
    login(['oa:org:view'])
    renderWithProviders(<Can code="oa:not:exist"><Button>危险</Button></Can>)
    expect(screen.queryByRole('button', { name: '危险' })).not.toBeInTheDocument()
  })

  it('★ permCodes 为空是全拒，不是放行', () => {
    // 这条守的是"清单为空 ⇒ 没有任何限制"这种一念之差的写法。
    // 后端 /me/permissions 未认证时返回 200 + 空清单，正好会命中它。
    login([])
    renderWithProviders(<Can code="oa:org:view"><Button>看组织</Button></Can>)
    expect(screen.queryByRole('button', { name: '看组织' })).not.toBeInTheDocument()
  })

  it('多个 code 默认 AND，any 时取 OR', () => {
    login(['oa:org:view'])
    const { unmount } = renderWithProviders(
      <Can code={['oa:org:view', 'oa:org:create']}><Button>AND</Button></Can>,
    )
    expect(screen.queryByRole('button', { name: 'AND' })).not.toBeInTheDocument()
    unmount()
    renderWithProviders(<Can code={['oa:org:view', 'oa:org:create']} any><Button>OR</Button></Can>)
    expect(screen.getByRole('button', { name: 'OR' })).toBeInTheDocument()
  })

  it('disable 模式渲染但置灰（"存在但你不能做" ≠ "不存在"）', () => {
    login([])
    renderWithProviders(<Can code="oa:org:create" mode="disable"><Button>新建</Button></Can>)
    expect(screen.getByRole('button', { name: btn('新建') })).toBeDisabled()
  })

  it('fallback 只在无权限时出现', () => {
    login([])
    renderWithProviders(<Can code="oa:org:create" fallback={<span>无权</span>}><Button>新建</Button></Can>)
    expect(screen.getByText('无权')).toBeInTheDocument()
  })
})

// ─────────────────────────────────────────────── <PermRoute>

function Guarded({ code, any }: { code: string | string[]; any?: boolean }) {
  return (
    <Routes>
      <Route path="/login" element={<div>登录页</div>} />
      <Route path="/" element={<PermRoute code={code} any={any}><div>受保护内容</div></PermRoute>} />
    </Routes>
  )
}

describe('<PermRoute>', () => {
  it('★ 加载中渲染骨架，不能渲染 403', () => {
    usePermStore.getState().reset() // loaded=false
    renderWithProviders(<Guarded code="oa:org:view" />)
    expect(screen.queryByText('无访问权限')).not.toBeInTheDocument()
    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument()
  })

  it('有权限放行', () => {
    login(['oa:org:view'])
    renderWithProviders(<Guarded code="oa:org:view" />)
    expect(screen.getByText('受保护内容')).toBeInTheDocument()
  })

  it('没权限给 403，而不是跳走', () => {
    login(['oa:employee:view'])
    renderWithProviders(<Guarded code="oa:org:view" />)
    expect(screen.getByText('无访问权限')).toBeInTheDocument()
    expect(screen.queryByText('登录页')).not.toBeInTheDocument()
  })

  it('未认证跳登录（而不是 403）', () => {
    usePermStore.getState().reset()
    usePermStore.getState().setAnonymous()
    renderWithProviders(<Guarded code="oa:org:view" />)
    expect(screen.getByText('登录页')).toBeInTheDocument()
  })

  it('未知 code 判拒', () => {
    login(['oa:org:view'])
    renderWithProviders(<Guarded code="oa:not:exist" />)
    expect(screen.getByText('无访问权限')).toBeInTheDocument()
  })
})

// ─────────────────────────────────────────────── usePerm

function Probe() {
  const p = usePerm()
  return (
    <div>
      <span data-testid="scope">{p.dataScopeLabel}</span>
      <span data-testid="org-scope">{p.scopeOf('org')}</span>
      <span data-testid="unknown-scope">{p.scopeOf('没这个模块')}</span>
      <span data-testid="elevated">{String(p.isElevated('oa:audit:view'))}</span>
      <span data-testid="version">{p.version}</span>
    </div>
  )
}

describe('usePerm', () => {
  it('数据范围有人话描述；模块范围缺省回落到合并范围', () => {
    login(['oa:org:view'], { dataScope: 'ORG_AND_SUB', moduleScope: { org: 'SELF' } })
    renderWithProviders(<Probe />)
    expect(screen.getByTestId('scope')).toHaveTextContent('本部门及下级')
    expect(screen.getByTestId('org-scope')).toHaveTextContent('SELF')
    expect(screen.getByTestId('unknown-scope')).toHaveTextContent('ORG_AND_SUB')
  })

  it('isElevated 只认后端下发的 elevatedCodes（持有权限 ≠ 已提权）', () => {
    login(['oa:audit:view'])
    renderWithProviders(<Probe />)
    expect(screen.getByTestId('elevated')).toHaveTextContent('false')
  })

  it('暴露 version，供 queryKey 使用', () => {
    login(['oa:org:view'], { version: 4200042 })
    renderWithProviders(<Probe />)
    expect(screen.getByTestId('version')).toHaveTextContent('4200042')
  })
})
