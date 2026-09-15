import { Alert, App, Button, Card, DatePicker, Form, Input, Popconfirm, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'
import { PERM } from '@oa/shared/perm/codes'
import { DataCard } from '../../components/common/DataCard'
import { usePerm, usePermVersion } from '../../auth/usePerm'

interface PermissionDelegationView {
  id: number
  direction: string
  delegatorIdentityId: string
  delegatorUserId: string
  delegateeIdentityId: string
  permCodes: string[]
  roleIds: number[]
  validFrom: string
  validTo: string
  reDelegate: boolean
  reason: string
  status: string
  createdAt: string
  revokedAt: string | null
}

interface Role { id: number; code: string; name: string }
interface IdentityOption { identityId: string; displayName: string; externalKey: string }

export default function PermissionDelegationPanel() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const perm = usePerm()
  const permVersion = usePermVersion()
  const [form] = Form.useForm()

  const list = useQuery({
    queryKey: ['iam-permission-delegations', permVersion],
    queryFn: async () =>
      (await apiClient.get('/api/v1/iam/permission-delegations?size=50')).data.data.items as PermissionDelegationView[],
    enabled: perm.has(PERM.IAM_DELEGATE),
  })

  const roles = useQuery({
    queryKey: ['iam-permission-delegation-roles', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/iam/permission-delegations/roles')).data.data as Role[],
    staleTime: 5 * 60_000,
    enabled: perm.has(PERM.IAM_DELEGATE),
  })

  const identities = useQuery({
    queryKey: ['iam-permission-delegation-users', permVersion],
    queryFn: async () =>
      (await apiClient.get('/api/v1/iam/identities?type=USER&size=50')).data.data.items as IdentityOption[],
    enabled: perm.has(PERM.IAM_IDENTITY_VIEW),
  })

  const create = useMutation({
    mutationFn: async (v: {
      delegateeIdentityId: string
      permCodes?: string[]
      roleIds?: number[]
      validTo: { toISOString: () => string }
      reason: string
    }) => apiClient.post('/api/v1/iam/permission-delegations', {
      delegateeIdentityId: v.delegateeIdentityId,
      permCodes: v.permCodes ?? [],
      roleIds: v.roleIds ?? [],
      validTo: v.validTo.toISOString(),
      reDelegate: false,
      reason: v.reason,
    }),
    onSuccess: () => {
      message.success('已创建权限委托；被委托人仅在时间窗内通过 Check')
      form.resetFields()
      qc.invalidateQueries({ queryKey: ['iam-permission-delegations'] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const revoke = useMutation({
    mutationFn: async (id: number) => apiClient.post(`/api/v1/iam/permission-delegations/${id}/revoke`),
    onSuccess: () => {
      message.success('已撤销权限委托')
      qc.invalidateQueries({ queryKey: ['iam-permission-delegations'] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const cols: ColumnsType<PermissionDelegationView> = [
    { title: '方向', dataIndex: 'direction', width: 100, render: (v) => <Tag>{v === 'OUTGOING' ? '我发出' : '我收到'}</Tag> },
    { title: '被委托人', dataIndex: 'delegateeIdentityId', ellipsis: true },
    { title: '权限点', dataIndex: 'permCodes', render: (v: string[]) => (v ?? []).join(', ') || '-' },
    { title: '有效期至', dataIndex: 'validTo', width: 180, render: (v) => new Date(v).toLocaleString('zh-CN') },
    { title: '事由', dataIndex: 'reason', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 100, render: (v) => <Tag color={v === 'ACTIVE' ? 'processing' : 'default'}>{v}</Tag> },
    {
      title: '操作', key: 'action', width: 90, render: (_, r) => r.direction === 'OUTGOING' && r.status === 'ACTIVE' ? (
        <Popconfirm title="撤销后立即失效" onConfirm={() => revoke.mutate(r.id)}>
          <Button type="link" size="small" danger loading={revoke.isPending}>撤销</Button>
        </Popconfirm>
      ) : null,
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="这是权限委托，不是待办代理"
        description="被委托人只在时间窗内通过 Check API。待办委托（/iam/delegations）不能用来调管理接口。"
      />
      <Card title="发出权限委托">
        <Form form={form} layout="vertical" onFinish={(v) => {
          if ((!v.permCodes || v.permCodes.length === 0) && (!v.roleIds || v.roleIds.length === 0)) {
            message.error('请填写权限点或选择角色')
            return
          }
          create.mutate(v)
        }}>
          {perm.has(PERM.IAM_IDENTITY_VIEW) ? (
            <Form.Item name="delegateeIdentityId" label="被委托人" rules={[{ required: true, message: '选择被委托人' }]}>
              <Select showSearch optionFilterProp="label" placeholder="人员身份" loading={identities.isLoading}
                options={(identities.data ?? []).map((i) => ({
                  value: i.identityId,
                  label: `${i.displayName}（${i.externalKey}）`,
                }))} />
            </Form.Item>
          ) : (
            <Form.Item name="delegateeIdentityId" label="被委托人身份 ID" rules={[{ required: true, whitespace: true }]}>
              <Input placeholder="identity UUID" />
            </Form.Item>
          )}
          <Form.Item name="permCodes" label="权限点">
            <Select mode="tags" placeholder="输入 oa:模块:动作 后回车" tokenSeparators={[',']} />
          </Form.Item>
          <Form.Item name="roleIds" label="或按角色展开">
            <Select mode="multiple" allowClear placeholder="可选" loading={roles.isLoading}
              options={(roles.data ?? []).map((r) => ({ value: r.id, label: `${r.name}（${r.code}）` }))} />
          </Form.Item>
          <Form.Item name="validTo" label="有效期至" rules={[{ required: true, message: '必须指定到期时间' }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="reason" label="事由" rules={[{ required: true, whitespace: true }]}>
            <Input.TextArea rows={2} maxLength={500} showCount />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={create.isPending}>创建委托</Button>
        </Form>
      </Card>
      <DataCard title="我的权限委托" query={list} data={list.data} emptyText="还没有权限委托">
        {(rows) => <Table rowKey="id" size="small" columns={cols} dataSource={rows} pagination={false} scroll={{ x: 960 }} />}
      </DataCard>
    </Space>
  )
}
