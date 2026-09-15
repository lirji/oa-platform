import { useState } from 'react'
import { Alert, App, Button, Card, Descriptions, Empty, Form, Input, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'
import { PERM } from '@oa/shared/perm/codes'
import { PageHeader } from '../../components/layout/PageHeader'
import { ErrorState, PageSkeleton } from '../../components/common/AsyncState'
import Can from '../../auth/Can'
import { usePerm, usePermVersion } from '../../auth/usePerm'
import type { IdentityView } from './IdentityCatalogPanel'

interface CredentialView {
  id: number
  identityId: string
  kind: string
  last4: string
  status: string
  expiresAt: string | null
  createdBy: string
  createdAt: string
  revokedAt: string | null
}

const LABEL_OPTIONS = [
  'EMPLOYEE', 'CONTRACTOR', 'ADMIN', 'PRIVILEGED',
  'SERVICE_ACCOUNT', 'AGENT', 'EXTERNAL', 'HIGH_RISK',
].map((value) => ({ value, label: value }))

export default function IdentityDetailPage() {
  const { identityId = '' } = useParams()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const qc = useQueryClient()
  const permVersion = usePermVersion()
  const perm = usePerm()
  const [form] = Form.useForm()
  const [issuedSecret, setIssuedSecret] = useState<string | null>(null)

  const query = useQuery({
    queryKey: ['iam-identity', identityId, permVersion],
    queryFn: async () => (await apiClient.get(`/api/v1/iam/identities/${identityId}`)).data.data as IdentityView,
    enabled: Boolean(identityId),
  })

  const graph = useQuery({
    queryKey: ['iam-identity-graph', identityId, permVersion],
    queryFn: async () => (await apiClient.get(`/api/v1/iam/identities/${identityId}/graph`)).data.data as {
      nodes: { id: string; type: string; label: string; refId: string }[]
      edges: { from: string; to: string; type: string }[]
    },
    enabled: Boolean(identityId),
  })

  const isNhi = Boolean(query.data && query.data.identityType !== 'USER')
  const creds = useQuery({
    queryKey: ['iam-identity-credentials', identityId, permVersion],
    queryFn: async () =>
      (await apiClient.get(`/api/v1/iam/identities/${identityId}/credentials`)).data.data as CredentialView[],
    enabled: Boolean(identityId) && isNhi,
  })

  const changeStatus = useMutation({
    mutationFn: async (status: string) =>
      apiClient.post(`/api/v1/iam/identities/${identityId}/status`, { status, version: query.data?.version }),
    onSuccess: () => {
      message.success('状态已更新')
      qc.invalidateQueries({ queryKey: ['iam-identity', identityId] })
      qc.invalidateQueries({ queryKey: ['iam-identities'] })
      qc.invalidateQueries({ queryKey: ['iam-identity-credentials', identityId] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const saveLabels = useMutation({
    mutationFn: async (labels: string[]) =>
      apiClient.put(`/api/v1/iam/identities/${identityId}/labels`, { labels, version: query.data?.version }),
    onSuccess: () => {
      message.success('标签已更新')
      qc.invalidateQueries({ queryKey: ['iam-identity', identityId] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const issue = useMutation({
    mutationFn: async () =>
      (await apiClient.post(`/api/v1/iam/identities/${identityId}/credentials`, {})).data.data as {
        credential: CredentialView
        secret: string
      },
    onSuccess: (data) => {
      setIssuedSecret(data.secret)
      message.success('已签发，明文只显示这一次')
      qc.invalidateQueries({ queryKey: ['iam-identity-credentials', identityId] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const revokeCred = useMutation({
    mutationFn: async (id: number) => apiClient.post(`/api/v1/iam/credentials/${id}/revoke`),
    onSuccess: () => {
      message.success('凭证已吊销')
      qc.invalidateQueries({ queryKey: ['iam-identity-credentials', identityId] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const rotateCred = useMutation({
    mutationFn: async (id: number) =>
      (await apiClient.post(`/api/v1/iam/credentials/${id}/rotate`)).data.data as {
        credential: CredentialView
        secret: string
      },
    onSuccess: (data) => {
      setIssuedSecret(data.secret)
      message.success('已轮换，请立刻保存新明文')
      qc.invalidateQueries({ queryKey: ['iam-identity-credentials', identityId] })
    },
    onError: (e) => message.error(errText(e)),
  })

  if (query.isLoading) return <PageSkeleton />
  if (query.isError) {
    return <ErrorState message={errText(query.error)} onRetry={() => query.refetch()} />
  }
  const row = query.data
  if (!row) return <ErrorState message="身份不存在" onRetry={() => navigate('/iam')} />

  return (
    <>
      <PageHeader title={row.displayName} description={`${row.identityType} · ${row.externalKey}`} />
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card>
          <Descriptions column={2} size="small">
            <Descriptions.Item label="identityId">{row.identityId}</Descriptions.Item>
            <Descriptions.Item label="状态"><Tag>{row.status}</Tag></Descriptions.Item>
            <Descriptions.Item label="来源">{row.source}</Descriptions.Item>
            <Descriptions.Item label="风险">{row.riskLevel}</Descriptions.Item>
            <Descriptions.Item label="员工 ID">{row.employeeId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="组织路径">{row.orgPath ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="属主">{row.ownerIdentityId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="过期">{row.expiredAt ? new Date(row.expiredAt).toLocaleString('zh-CN') : '无'}</Descriptions.Item>
            <Descriptions.Item label="标签" span={2}>
              {(row.labels ?? []).map((l) => <Tag key={l}>{l}</Tag>)}
            </Descriptions.Item>
          </Descriptions>
        </Card>
        <Card title="关系图谱（1 跳）">
          {graph.isLoading ? (
            <PageSkeleton rows={3} />
          ) : graph.isError ? (
            <ErrorState message={errText(graph.error)} onRetry={graph.refetch} />
          ) : !(graph.data?.edges?.length) ? (
            <Empty description="暂无部门、属主或角色关系" />
          ) : (
            <Table
              size="small"
              rowKey={(e) => `${e.type}-${e.from}-${e.to}`}
              pagination={false}
              dataSource={graph.data.edges}
              columns={[
                {
                  title: '关系', dataIndex: 'type', width: 120,
                  render: (type: string) => <Tag>{type}</Tag>,
                },
                {
                  title: '从', dataIndex: 'from',
                  render: (id: string) => graph.data.nodes.find((n) => n.id === id)?.label ?? id,
                },
                {
                  title: '到', dataIndex: 'to',
                  render: (id: string) => graph.data.nodes.find((n) => n.id === id)?.label ?? id,
                },
              ]}
            />
          )}
        </Card>
        {row.identityType === 'AGENT' && (
          <AgentInvokeCard
            identityId={row.identityId}
            canCheck={perm.has(PERM.IAM_CHECK) || perm.has(PERM.IAM_ADMIN)}
          />
        )}
        {row.identityType !== 'USER' && (
          <Card title="凭证" extra={
            <Can code={PERM.IAM_IDENTITY_ADMIN}>
              <Button type="primary" onClick={() => issue.mutate()} loading={issue.isPending}
                disabled={row.status !== 'ACTIVE' && row.status !== 'CREATED'}>签发</Button>
            </Can>
          }>
            {issuedSecret && (
              <Alert type="warning" showIcon closable onClose={() => setIssuedSecret(null)}
                style={{ marginBottom: 12 }}
                message="明文只出现这一次，关闭后无法再查看"
                description={<code>{issuedSecret}</code>} />
            )}
            {creds.isLoading ? (
              <PageSkeleton rows={3} />
            ) : creds.isError ? (
              <ErrorState message={errText(creds.error)} onRetry={creds.refetch} />
            ) : !(creds.data?.length) ? (
              <Empty description="还没有凭证" />
            ) : (
              <Table
                size="small"
                rowKey="id"
                pagination={false}
                dataSource={creds.data}
                columns={[
                  { title: '类型', dataIndex: 'kind', width: 140, render: (v) => <Tag>{v}</Tag> },
                  { title: '末四位', dataIndex: 'last4', width: 90 },
                  { title: '状态', dataIndex: 'status', width: 110, render: (v) => <Tag>{v}</Tag> },
                  { title: '签发人', dataIndex: 'createdBy', width: 120 },
                  {
                    title: '操作', key: 'op', width: 160,
                    render: (_, c) => c.status === 'ACTIVE' ? (
                      <Can code={PERM.IAM_IDENTITY_ADMIN}>
                        <Space>
                          <Popconfirm title="轮换后旧密钥立即失效" onConfirm={() => rotateCred.mutate(c.id)}>
                            <Button type="link" size="small" loading={rotateCred.isPending}>轮换</Button>
                          </Popconfirm>
                          <Popconfirm title="吊销后不可恢复" onConfirm={() => revokeCred.mutate(c.id)}>
                            <Button type="link" size="small" danger loading={revokeCred.isPending}>吊销</Button>
                          </Popconfirm>
                        </Space>
                      </Can>
                    ) : null,
                  },
                ]}
              />
            )}
          </Card>
        )}
        <Can code={PERM.IAM_IDENTITY_ADMIN}>
          <Card title="治理操作">
            <Space wrap>
              {row.status === 'ACTIVE' && (
                <Button onClick={() => changeStatus.mutate('SUSPENDED')} loading={changeStatus.isPending}>停用</Button>
              )}
              {row.status === 'SUSPENDED' && (
                <Button onClick={() => changeStatus.mutate('ACTIVE')} loading={changeStatus.isPending}>恢复</Button>
              )}
              {(row.status === 'ACTIVE' || row.status === 'SUSPENDED') && (
                <Button danger onClick={() => changeStatus.mutate('DISABLED')} loading={changeStatus.isPending}>禁用</Button>
              )}
              {row.status === 'DISABLED' && (
                <Button onClick={() => changeStatus.mutate('ACTIVE')} loading={changeStatus.isPending}>重新启用</Button>
              )}
            </Space>
            <Form form={form} layout="vertical" style={{ marginTop: 16 }}
              initialValues={{ labels: row.labels }}
              onFinish={(v) => saveLabels.mutate(v.labels ?? [])}>
              <Form.Item name="labels" label="标签（全量替换）">
                <Select mode="multiple" options={LABEL_OPTIONS} />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={saveLabels.isPending}>保存标签</Button>
              </Form.Item>
            </Form>
          </Card>
        </Can>
      </Space>
    </>
  )
}

function AgentInvokeCard({ identityId, canCheck }: { identityId: string; canCheck: boolean }) {
  const permVersion = usePermVersion()
  const [tool, setTool] = useState('kb.search')
  const invoke = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post('/api/v1/authz/check', {
        principal: { identityId, identityType: 'AGENT' },
        resource: { type: 'TOOL', id: tool, attributes: {} },
        action: 'INVOKE',
        environment: { tool },
        commandId: `agent-invoke-${Date.now()}`,
      })
      return res.data.data as {
        decision: string
        policyId: string
        reason: string
        traceId: string
        evaluatedAt: string
      }
    },
  })
  const logs = useQuery({
    queryKey: ['iam-agent-invocations', identityId, permVersion],
    queryFn: async () =>
      (await apiClient.get(
        `/api/v1/iam/admin/decisions?identityId=${encodeURIComponent(identityId)}&size=5`,
      )).data.data as {
        items: Array<{ id: number; decision: string; action: string; resourceId: string; policyId: string }>
      },
    enabled: canCheck && Boolean(identityId),
    staleTime: 0,
  })

  return (
    <Card title="Agent INVOKE 试算">
      {!canCheck ? (
        <Alert type="warning" showIcon message="需要 oa:iam:check 或管理员才能试算 Agent 调用" />
      ) : (
        <Space direction="vertical" style={{ width: '100%' }} size={8}>
          <Input value={tool} onChange={(e) => setTool(e.target.value)} placeholder="工具或权限点，如 kb.search" />
          <Button type="primary" disabled={!tool.trim()} loading={invoke.isPending}
            onClick={() => { void invoke.mutateAsync().then(() => logs.refetch()) }}>
            调用 Check INVOKE
          </Button>
          {invoke.isError && <Alert type="error" showIcon message={errText(invoke.error)} />}
          {invoke.data && (
            <Space wrap>
              <Tag color={invoke.data.decision === 'ALLOW' ? 'success' : 'error'}>{invoke.data.decision}</Tag>
              <Tag>{invoke.data.policyId}</Tag>
              <Typography.Text type="secondary">{invoke.data.reason}</Typography.Text>
            </Space>
          )}
          {logs.isError ? (
            <ErrorState message={errText(logs.error)} onRetry={logs.refetch} />
          ) : logs.data?.items?.length ? (
            <Typography.Text type="secondary">
              最近审计 {logs.data.items[0].action} {logs.data.items[0].resourceId} · {logs.data.items[0].decision}
            </Typography.Text>
          ) : null}
        </Space>
      )}
    </Card>
  )
}
