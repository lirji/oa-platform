import { Alert, Button, Card, Descriptions, Drawer, Space, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'

interface Basis {
  requestId: string; requestVersion: number; snapshotHash: string; applicationId: string; environment: string
  membershipId: string; generation: number; roleId: string; capabilities: string[]
  scopeRule: { resourceType: string; clauses: { kind: string; values: string[]; includeRoot: boolean }[] }
  validFrom: string; validTo: string; reason: string
}
const scopeLabels: Record<string, string> = { TENANT_ALL: '当前企业全部资源', SPECIFIED_STORES: '指定门店', SPECIFIED_RESOURCES: '指定资源' }

/** 审批前读取任务实际指派下的固定申请，不从待办摘要或客户端参数重建授权内容。 */
export function CentralAccessReview({ task, busy, close, complete, permissionVersion }: {
  task: string; busy: boolean; close: () => void; complete: (outcome: 'APPROVE' | 'REJECT') => void; permissionVersion: unknown
}) {
  // 快照不可变；切回标签页不触发按钮瞬时禁用，权限版本变化或手动刷新仍重新读取。
  const basis = useQuery({ queryKey: ['central-access-review', task, permissionVersion], queryFn: async () =>
    (await apiClient.get(`/api/v1/flow/central-access/tasks/${encodeURIComponent(task)}`)).data.data as Basis, retry: false, staleTime: 0, gcTime: 0, refetchOnWindowFocus: false })
  const data = !basis.error ? basis.data : undefined
  return <Drawer title="权限申请审批依据" open width={660} onClose={() => { if (!busy) close() }} extra={<Button onClick={() => void basis.refetch()}>刷新依据</Button>}>
    {basis.error ? <Alert type="error" showIcon message={errText(basis.error)} action={<Button onClick={() => void basis.refetch()}>重试</Button>} /> : <Card loading={basis.isPending}>
      {data && <>
        <Alert type="info" showIcon message="审批只确认此固定申请。实际权限仍须由认证平台复核并完成投影。" style={{ marginBottom: 16 }} />
        <Descriptions column={1} bordered items={[
          { label: '申请编号 / 版本', children: `${data.requestId} / ${data.requestVersion}` },
          { label: '申请成员 / 代际', children: `${data.membershipId} / ${data.generation}` },
          { label: '应用 / 环境', children: `${data.applicationId} / ${data.environment}` },
          { label: '申请原因', children: data.reason }, { label: '固定能力', children: data.capabilities.join('、') },
          { label: '固定范围', children: <>{data.scopeRule.resourceType}{data.scopeRule.clauses.map((c, i) => <div key={i}>{scopeLabels[c.kind] ?? c.kind}：{c.values.join('、') || '全部'}{c.includeRoot ? '（包含根节点）' : ''}</div>)}</> },
          { label: '固定有效期', children: `${new Date(data.validFrom).toLocaleString()} — ${new Date(data.validTo).toLocaleString()}` },
          { label: '快照指纹', children: <Typography.Text copyable style={{ overflowWrap: 'anywhere' }}>{data.snapshotHash}</Typography.Text> },
        ]} />
        <Space style={{ marginTop: 20 }}><Button type="primary" loading={busy} disabled={basis.isFetching} onClick={() => complete('APPROVE')}>同意此固定申请</Button><Button danger loading={busy} disabled={basis.isFetching} onClick={() => complete('REJECT')}>驳回此申请</Button></Space>
      </>}
    </Card>}
  </Drawer>
}
