import { useMemo, useState } from 'react'
import { App, Button, Form, Input, Modal, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'
import { DataCard } from '../../components/common/DataCard'
import Can from '../../auth/Can'
import { usePermVersion } from '../../auth/usePerm'

export interface IdentityView {
  identityId: string
  tenantId: number
  identityType: string
  displayName: string
  source: string
  status: string
  externalKey: string
  employeeId: number | null
  orgId: number | null
  orgPath: string | null
  riskLevel: string
  ownerIdentityId: string | null
  expiredAt: string | null
  version: number
  labels: string[]
  createdAt: string
  updatedAt: string
}

interface IdentityPage {
  items: IdentityView[]
  nextCursor: string
  hasMore: boolean
}

const TYPE_OPTIONS = [
  { value: 'USER', label: '人员' },
  { value: 'SERVICE_ACCOUNT', label: '服务账号' },
  { value: 'API_CLIENT', label: 'API 客户端' },
  { value: 'APPLICATION', label: '应用' },
  { value: 'AGENT', label: 'Agent' },
  { value: 'BOT', label: 'Bot' },
  { value: 'AUTOMATION_WORKER', label: '自动化工人' },
]

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  DISABLED: 'default',
  CREATED: 'processing',
  EXPIRED: 'error',
}

export default function IdentityCatalogPanel() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const permVersion = usePermVersion()
  const [type, setType] = useState<string | undefined>()
  const [status, setStatus] = useState<string | undefined>()
  const [q, setQ] = useState('')
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()

  const query = useQuery({
    queryKey: ['iam-identities', type, status, q, permVersion],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (type) params.set('type', type)
      if (status) params.set('status', status)
      if (q.trim()) params.set('q', q.trim())
      params.set('size', '50')
      const res = await apiClient.get(`/api/v1/iam/identities?${params.toString()}`)
      return res.data.data as IdentityPage
    },
  })

  const create = useMutation({
    mutationFn: async (body: {
      identityType: string
      displayName: string
      externalKey: string
      ownerIdentityId?: string
    }) => apiClient.post('/api/v1/iam/identities', body),
    onSuccess: () => {
      message.success('已创建非人身份')
      setOpen(false)
      form.resetFields()
      qc.invalidateQueries({ queryKey: ['iam-identities'] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const columns: ColumnsType<IdentityView> = useMemo(() => [
    {
      title: '名称', dataIndex: 'displayName',
      render: (name: string, row) => <Link to={`/iam/identities/${row.identityId}`}>{name}</Link>,
    },
    { title: '类型', dataIndex: 'identityType', width: 160 },
    { title: '来源键', dataIndex: 'externalKey', ellipsis: true },
    {
      title: '状态', dataIndex: 'status', width: 110,
      render: (v: string) => <Tag color={STATUS_COLOR[v] ?? 'default'}>{v}</Tag>,
    },
    {
      title: '标签', dataIndex: 'labels', width: 200,
      render: (labels: string[]) => (labels ?? []).map((l) => <Tag key={l}>{l}</Tag>),
    },
  ], [])

  return (
    <>
      <DataCard
        title="身份目录"
        extra={
          <Space wrap>
            <Select allowClear placeholder="类型" style={{ width: 160 }} options={TYPE_OPTIONS}
              value={type} onChange={setType} />
            <Select allowClear placeholder="状态" style={{ width: 140 }}
              options={['ACTIVE', 'SUSPENDED', 'DISABLED', 'CREATED', 'EXPIRED'].map((v) => ({ value: v, label: v }))}
              value={status} onChange={setStatus} />
            <Input.Search allowClear placeholder="名称或来源键" style={{ width: 220 }}
              onSearch={setQ} />
            <Can code="oa:iam:identity:admin">
              <Button type="primary" onClick={() => setOpen(true)}>新建非人身份</Button>
            </Can>
          </Space>
        }
        query={query}
        data={query.data?.items}
        emptyText="还没有身份。员工入职后会自动投影为人员身份。"
      >
        {(rows) => <Table rowKey="identityId" columns={columns} dataSource={rows} pagination={false} scroll={{ x: 900 }} />}
      </DataCard>
      <Modal title="新建非人身份" open={open} onCancel={() => setOpen(false)}
        onOk={() => form.submit()} confirmLoading={create.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => create.mutate(v)}>
          <Form.Item name="identityType" label="类型" rules={[{ required: true }]} initialValue="SERVICE_ACCOUNT">
            <Select options={TYPE_OPTIONS.filter((o) => o.value !== 'USER')} />
          </Form.Item>
          <Form.Item name="displayName" label="名称" rules={[{ required: true, whitespace: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="externalKey" label="来源键" rules={[{ required: true, whitespace: true }]}
            extra="同一类型下唯一，例如 client-id / agent-name">
            <Input />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.identityType !== c.identityType}>
            {() => form.getFieldValue('identityType') === 'AGENT' ? (
              <Form.Item name="ownerIdentityId" label="属主身份 ID" rules={[{ required: true, whitespace: true }]}
                extra="必须是人员 USER 的 identityId，Agent 不继承属主权限">
                <Input />
              </Form.Item>
            ) : null}
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
