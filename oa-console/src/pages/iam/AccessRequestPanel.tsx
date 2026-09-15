import { App, Button, Form, Input, Popconfirm, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'
import { PERM } from '@oa/shared/perm/codes'
import { DataCard } from '../../components/common/DataCard'
import { usePerm, usePermVersion } from '../../auth/usePerm'

interface AccessRequestView {
  id: number
  requesterUserId: string
  requestType: string
  roleId: number
  roleCode: string
  roleName: string
  reason: string
  status: string
  grantId: number | null
  approvalInstanceId: string | null
  requestedAt: string
}

interface Role { id: number; code: string; name: string }

export default function AccessRequestPanel() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const perm = usePerm()
  const permVersion = usePermVersion()
  const [form] = Form.useForm()
  const isAdmin = perm.has(PERM.IAM_ADMIN)

  const mine = useQuery({
    queryKey: ['iam-access-requests-mine', permVersion],
    queryFn: async () =>
      (await apiClient.get('/api/v1/iam/access-requests/mine?size=50')).data.data.items as AccessRequestView[],
    enabled: perm.has(PERM.IAM_REQUEST),
  })

  const pending = useQuery({
    queryKey: ['iam-access-requests-pending', permVersion],
    queryFn: async () =>
      (await apiClient.get('/api/v1/iam/access-requests?status=PENDING&size=50')).data.data.items as AccessRequestView[],
    enabled: isAdmin,
  })

  const roles = useQuery({
    queryKey: ['iam-access-request-roles', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/iam/access-requests/roles')).data.data as Role[],
    staleTime: 5 * 60_000,
    enabled: perm.has(PERM.IAM_REQUEST),
  })

  const create = useMutation({
    mutationFn: async (body: { requestType: string; roleId: number; reason: string }) =>
      apiClient.post('/api/v1/iam/access-requests', body),
    onSuccess: () => {
      message.success('已提交，等待四眼审批')
      form.resetFields()
      qc.invalidateQueries({ queryKey: ['iam-access-requests-mine'] })
      qc.invalidateQueries({ queryKey: ['iam-access-requests-pending'] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const decide = useMutation({
    mutationFn: async ({ id, action }: { id: number; action: 'approve' | 'reject' }) =>
      apiClient.post(`/api/v1/iam/access-requests/${id}/${action}`, {
        reason: action === 'approve' ? '权限管理员批准' : '权限管理员拒绝',
      }),
    onSuccess: (_, v) => {
      message.success(v.action === 'approve' ? '已批准，授权立即生效' : '已拒绝申请')
      qc.invalidateQueries({ queryKey: ['iam-access-requests-pending'] })
      qc.invalidateQueries({ queryKey: ['iam-access-requests-mine'] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const mineCols: ColumnsType<AccessRequestView> = [
    { title: '类型', dataIndex: 'requestType', width: 110, render: (v) => <Tag>{v}</Tag> },
    { title: '角色', key: 'role', render: (_, r) => `${r.roleName ?? ''}（${r.roleCode ?? r.roleId}）` },
    { title: '事由', dataIndex: 'reason', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 110, render: (v) => <Tag>{v}</Tag> },
    { title: '申请时间', dataIndex: 'requestedAt', width: 180, render: (v) => new Date(v).toLocaleString('zh-CN') },
  ]

  const pendingCols: ColumnsType<AccessRequestView> = [
    { title: '申请人', dataIndex: 'requesterUserId', width: 140 },
    ...mineCols,
    {
      title: '操作', key: 'action', width: 160, render: (_, r) => (
        <Space>
          <Popconfirm title="批准后立即写入授权" onConfirm={() => decide.mutate({ id: r.id, action: 'approve' })}>
            <Button type="link" size="small" loading={decide.isPending}>批准</Button>
          </Popconfirm>
          <Popconfirm title="拒绝该申请？" onConfirm={() => decide.mutate({ id: r.id, action: 'reject' })}>
            <Button type="link" danger size="small" loading={decide.isPending}>拒绝</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <DataCard title="提交权限申请" query={roles} data={roles.data} emptyText="没有可申请的角色">
        {() => (
          <Form form={form} layout="inline" onFinish={(v) => create.mutate(v)}>
            <Form.Item name="requestType" initialValue="ROLE" hidden>
              <Input />
            </Form.Item>
            <Form.Item name="roleId" rules={[{ required: true, message: '选择角色' }]}>
              <Select style={{ width: 220 }} placeholder="角色"
                options={(roles.data ?? []).map((r) => ({ value: r.id, label: `${r.name}（${r.code}）` }))} />
            </Form.Item>
            <Form.Item name="reason" rules={[{ required: true, whitespace: true }]}>
              <Input placeholder="申请事由" style={{ width: 280 }} />
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" loading={create.isPending}>提交</Button>
            </Form.Item>
          </Form>
        )}
      </DataCard>
      <DataCard title="我的申请" query={mine} data={mine.data} emptyText="还没有权限申请">
        {(rows) => <Table rowKey="id" size="small" columns={mineCols} dataSource={rows} pagination={false} />}
      </DataCard>
      {isAdmin && (
        <DataCard title="待审批" query={pending} data={pending.data} emptyText="当前没有待审批申请">
          {(rows) => <Table rowKey="id" size="small" columns={pendingCols} dataSource={rows} pagination={false} scroll={{ x: 900 }} />}
        </DataCard>
      )}
    </Space>
  )
}
