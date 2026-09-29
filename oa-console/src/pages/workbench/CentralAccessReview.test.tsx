import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CentralAccessReview } from './CentralAccessReview'
const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@oa/shared/api/client', () => ({ apiClient: { get } }))
const basis = { requestId: 'R1', requestVersion: 1, snapshotHash: 'fixed-hash', applicationId: 'commerce', environment: 'test', membershipId: 'M1', generation: 2, roleId: 'V1', capabilities: ['commerce.product.export'], scopeRule: { resourceType: 'product', clauses: [{ kind: 'SPECIFIED_STORES', values: ['S1'], includeRoot: false }] }, validFrom: '2026-09-29T00:00:00Z', validTo: '2026-09-29T01:00:00Z', reason: '商品核对' }
function show() {
  const complete = vi.fn(), client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = render(<QueryClientProvider client={client}><CentralAccessReview task="T1" busy={false} close={vi.fn()} complete={complete} permissionVersion={1} /></QueryClientProvider>)
  return { complete, client, view }
}
afterEach(() => { cleanup(); get.mockReset() })
describe('中央申请审批依据', () => {
  it('未取得固定依据时不给出审批入口', async () => {
    get.mockRejectedValue(new Error('不可用'))
    const { complete } = show()
    await screen.findByRole('alert')
    expect(screen.queryByRole('button', { name: '同意此固定申请' })).not.toBeInTheDocument()
    expect(complete).not.toHaveBeenCalled()
  })
  it('展示原范围和快照，明确动作只传递审批结果', async () => {
    get.mockResolvedValue({ data: { data: basis } })
    const { complete } = show()
    await screen.findByText('指定门店：S1')
    expect(screen.getByText('fixed-hash')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '同意此固定申请' }))
    expect(complete).toHaveBeenCalledWith('APPROVE')
    expect(get).toHaveBeenCalledWith('/api/v1/flow/central-access/tasks/T1')
  })
  it('手动刷新失败后清除旧审批入口，不能沿用旧依据允许办理', async () => {
    get.mockResolvedValueOnce({ data: { data: basis } }).mockRejectedValue(new Error('权限已变化'))
    show(); await screen.findByRole('button', { name: '同意此固定申请' })
    fireEvent.click(screen.getByRole('button', { name: '刷新依据' }))
    await screen.findByRole('button', { name: /重\s*试/ })
    expect(screen.queryByRole('button', { name: '同意此固定申请' })).not.toBeInTheDocument()
  })
})
