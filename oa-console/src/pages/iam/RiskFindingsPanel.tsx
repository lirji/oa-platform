import { App, Button, Popconfirm, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'
import { PERM } from '@oa/shared/perm/codes'
import { DataCard } from '../../components/common/DataCard'
import { usePerm, usePermVersion } from '../../auth/usePerm'

interface RiskFindingView {
  id: number
  ruleCode: string
  severity: string
  identityId: string
  identityType: string
  displayName: string
  externalKey: string
  summary: string
  status: string
  detectedAt: string
}

const RULE_LABEL: Record<string, string> = {
  STALE_UNUSED: '长期未用',
  LEAVER_RESIDUAL: '离职残留',
  AGENT_ORPHAN: 'Agent 无主',
}

const STATUS_COLOR: Record<string, string> = {
  OPEN: 'error',
  ACKNOWLEDGED: 'warning',
  RESOLVED: 'success',
  IGNORED: 'default',
}

export default function RiskFindingsPanel() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const perm = usePerm()
  const permVersion = usePermVersion()
  const canViewIdentity = perm.has(PERM.IAM_IDENTITY_VIEW)

  const findings = useQuery({
    queryKey: ['iam-risk-findings', permVersion],
    queryFn: async () =>
      (await apiClient.get('/api/v1/iam/risk/findings?size=100')).data.data.items as RiskFindingView[],
    enabled: perm.has(PERM.IAM_ADMIN),
  })

  const scan = useMutation({
    mutationFn: async () => (await apiClient.post('/api/v1/iam/risk/scan')).data.data as { opened: number },
    onSuccess: (data) => {
      message.success(`扫描完成，新开 ${data.opened} 条`)
      qc.invalidateQueries({ queryKey: ['iam-risk-findings'] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const changeStatus = useMutation({
    mutationFn: async ({ id, status }: { id: number; status: string }) =>
      apiClient.post(`/api/v1/iam/risk/findings/${id}/status`, { status }),
    onSuccess: () => {
      message.success('已更新状态')
      qc.invalidateQueries({ queryKey: ['iam-risk-findings'] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const cols: ColumnsType<RiskFindingView> = [
    { title: '规则', dataIndex: 'ruleCode', width: 130, render: (v) => <Tag>{RULE_LABEL[v] ?? v}</Tag> },
    { title: '级别', dataIndex: 'severity', width: 90, render: (v) => <Tag color={v === 'HIGH' ? 'error' : 'warning'}>{v}</Tag> },
    {
      title: '身份',
      key: 'identity',
      render: (_, r) => canViewIdentity
        ? <Link to={`/iam/identities/${r.identityId}`}>{r.displayName || r.externalKey}</Link>
        : (r.displayName || r.externalKey),
    },
    { title: '类型', dataIndex: 'identityType', width: 140, render: (v) => <Tag>{v}</Tag> },
    { title: '说明', dataIndex: 'summary', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 120, render: (v) => <Tag color={STATUS_COLOR[v]}>{v}</Tag> },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, r) => r.status === 'OPEN' || r.status === 'ACKNOWLEDGED' ? (
        <Space>
          {r.status === 'OPEN' && (
            <Button type="link" size="small" loading={changeStatus.isPending}
              onClick={() => changeStatus.mutate({ id: r.id, status: 'ACKNOWLEDGED' })}>
              确认
            </Button>
          )}
          <Popconfirm title="标记为已解决？" onConfirm={() => changeStatus.mutate({ id: r.id, status: 'RESOLVED' })}>
            <Button type="link" size="small" loading={changeStatus.isPending}>解决</Button>
          </Popconfirm>
          <Popconfirm title="忽略该发现？" onConfirm={() => changeStatus.mutate({ id: r.id, status: 'IGNORED' })}>
            <Button type="link" size="small" danger loading={changeStatus.isPending}>忽略</Button>
          </Popconfirm>
        </Space>
      ) : null,
    },
  ]

  return (
    <DataCard
      title="风险发现"
      extra={
        <Button type="primary" loading={scan.isPending} onClick={() => scan.mutate()} data-testid="primary-action">
          扫描
        </Button>
      }
      query={findings}
      data={findings.data}
      emptyText="暂无发现。点「扫描」跑内置规则（长期未用 / 离职残留 / Agent 无主）"
    >
      {(rows) => (
        <Table rowKey="id" columns={cols} dataSource={rows} pagination={false} size="middle" scroll={{ x: 1100 }} />
      )}
    </DataCard>
  )
}
