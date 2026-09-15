import { useState } from 'react'
import { App, Button, Descriptions, Drawer, Form, Input, Modal, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { EyeOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { normalizeError } from '@oa/shared/api/errors'
import { PERM } from '@oa/shared/perm/codes'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataCard } from '../../components/common/DataCard'
import Can from '../../auth/Can'
import { usePermVersion } from '../../auth/usePerm'
import { useElevationFlow } from '../../auth/useElevationFlow'

/** springdoc 里的 DocView 被流程单据占了；这里对齐 Java DocDtos.DocView。 */
interface OfficialDoc {
  id: number
  direction: string
  docNumber: string | null
  title: string
  body: string | null
  docType: string
  urgency: string
  secrecy: string
  status: string
  drafterId: string
  issuedAt: string | null
  archivedAt: string | null
  orgId: number | null
}

const LIST_KEY = 'official-docs'
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default', REVIEWING: 'processing', ISSUED: 'success', ARCHIVED: 'gold',
}

export default function OfficialDocPage() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const permVersion = usePermVersion()
  const elevate = useElevationFlow()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [openDraft, setOpenDraft] = useState(false)
  const [selected, setSelected] = useState<OfficialDoc | null>(null)
  const [form] = Form.useForm()

  const list = useQuery({
    queryKey: [LIST_KEY, keyword, status, permVersion],
    queryFn: async () => {
      const params = new URLSearchParams({ limit: '50' })
      if (keyword.trim()) params.set('keyword', keyword.trim())
      if (status) params.set('status', status)
      return (await apiClient.get(`/api/v1/doc/official?${params}`)).data.data as OfficialDoc[]
    },
  })

  const onErr = (e: unknown) => {
    const n = normalizeError(e)
    if (n.kind === 'needElevation') { void elevate.request(n); return }
    message.error(n.text)
  }

  const draft = useMutation({
    mutationFn: async (v: { direction: string; title: string; body?: string; docType?: string; urgency?: string; secrecy?: string; sourceOrg?: string }) =>
      apiClient.post('/api/v1/doc/official', v),
    onSuccess: () => {
      message.success('拟稿已保存')
      setOpenDraft(false)
      form.resetFields()
      void qc.invalidateQueries({ queryKey: [LIST_KEY] })
    },
    onError: onErr,
  })

  const issue = useMutation({
    mutationFn: async (id: number) => apiClient.post(`/api/v1/doc/official/${id}/issue`),
    onSuccess: () => { message.success('已核发'); void qc.invalidateQueries({ queryKey: [LIST_KEY] }) },
    onError: onErr,
  })

  const archive = useMutation({
    mutationFn: async (id: number) => apiClient.post(`/api/v1/doc/official/${id}/archive`),
    onSuccess: () => { message.success('已归档'); void qc.invalidateQueries({ queryKey: [LIST_KEY] }) },
    onError: onErr,
  })

  const cols: ColumnsType<OfficialDoc> = [
    { title: '文号', dataIndex: 'docNumber', width: 180, render: (v) => v ?? '—' },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '方向', dataIndex: 'direction', width: 80, render: (v: string) => <Tag>{v === 'IN' ? '收文' : '发文'}</Tag> },
    { title: '紧急', dataIndex: 'urgency', width: 90, render: (v) => <Tag color={v === 'URGENT' ? 'red' : 'default'}>{v}</Tag> },
    { title: '状态', dataIndex: 'status', width: 110, render: (v: string) => <Tag color={STATUS_COLOR[v] ?? 'default'}>{v}</Tag> },
    {
      title: '操作', key: 'op', width: 220,
      render: (_, row) => (
        <Space>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => setSelected(row)}>查看</Button>
          {['DRAFT', 'REVIEWING'].includes(row.status) && (
            <Can code={PERM.DOC_ISSUE}>
              <Button type="link" size="small" loading={issue.isPending} onClick={() => issue.mutate(row.id)}>核发</Button>
            </Can>
          )}
          {row.status === 'ISSUED' && (
            <Can code={PERM.DOC_ARCHIVE}>
              <Button type="link" size="small" loading={archive.isPending} onClick={() => archive.mutate(row.id)}>归档</Button>
            </Can>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="公文"
        description="列表受数据范围裁剪。核发只允许草稿/审核中，归档只允许已核发。"
        extra={
          <Space>
            <Input.Search allowClear placeholder="标题 / 正文" value={keyword}
              onChange={(e) => setKeyword(e.target.value)} style={{ width: 240 }} />
            <Select allowClear placeholder="状态" style={{ width: 140 }} value={status} onChange={setStatus}
              options={['DRAFT', 'REVIEWING', 'ISSUED', 'ARCHIVED'].map((v) => ({ value: v, label: v }))} />
            <Can code={PERM.DOC_DRAFT}>
              <Button type="primary" onClick={() => setOpenDraft(true)} data-testid="primary-action">拟稿</Button>
            </Can>
          </Space>
        }
      />
      <DataCard query={list} data={list.data} scoped scopeModule="doc" emptyText="暂无可见公文">
        {(rows) => <Table rowKey="id" columns={cols} dataSource={rows} pagination={{ pageSize: 15 }} scroll={{ x: 960 }} size="middle" />}
      </DataCard>

      <Drawer title={selected?.title ?? '公文'} width={640} open={selected != null} onClose={() => setSelected(null)} destroyOnClose>
        {selected && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions column={1} bordered size="small" items={[
              { key: 'no', label: '文号', children: selected.docNumber ?? '未编号' },
              { key: 'dir', label: '方向', children: selected.direction === 'IN' ? '收文' : '发文' },
              { key: 'type', label: '类型', children: selected.docType },
              { key: 'urg', label: '紧急程度', children: selected.urgency },
              { key: 'sec', label: '密级', children: selected.secrecy },
              { key: 'st', label: '状态', children: selected.status },
              { key: 'issued', label: '核发时间', children: selected.issuedAt ? new Date(selected.issuedAt).toLocaleString('zh-CN') : '—' },
            ]} />
            <div style={{ whiteSpace: 'pre-wrap' }}>{selected.body || '（无正文）'}</div>
          </Space>
        )}
      </Drawer>

      <Modal title="拟稿" open={openDraft} onCancel={() => setOpenDraft(false)}
        onOk={() => form.submit()} confirmLoading={draft.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => draft.mutate(v)}
          initialValues={{ direction: 'OUT', docType: 'NOTICE', urgency: 'NORMAL', secrecy: 'PUBLIC' }}>
          <Form.Item name="direction" label="方向" rules={[{ required: true }]}>
            <Select options={[{ value: 'OUT', label: '发文' }, { value: 'IN', label: '收文' }]} />
          </Form.Item>
          <Form.Item name="title" label="标题" rules={[{ required: true, whitespace: true }]}>
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item name="body" label="正文"><Input.TextArea rows={5} /></Form.Item>
          <Form.Item name="sourceOrg" label="来文机关"><Input placeholder="收文时填写" /></Form.Item>
        </Form>
      </Modal>
      {elevate.dialog}
    </>
  )
}
