import { useState, type ReactNode } from 'react'
import { App, Badge, Button, Descriptions, Drawer, Form, Input, Modal, Select, Space, Table, Tabs, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '@oa/shared/api/client'
import { normalizeError } from '@oa/shared/api/errors'
import { PERM } from '@oa/shared/perm/codes'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataCard } from '../../components/common/DataCard'
import Can from '../../auth/Can'
import { usePerm, usePermVersion } from '../../auth/usePerm'
import { useElevationFlow } from '../../auth/useElevationFlow'

interface NotificationView {
  id: number
  category: string
  title: string
  content: string | null
  bizType: string | null
  bizId: string | null
  link: string | null
  read: boolean
  createdAt: string
}

interface AnnouncementView {
  id: number
  title: string
  content: string
  publisherId: string
  publisherName: string | null
  audienceCount: number
  status: string
  publishedAt: string | null
  expireAt: string | null
  readByMe: boolean | null
}

interface ReadStats {
  announcementId: number
  audienceCount: number
  readCount: number
  unreadCount: number
  bitmapBytes: number
}

interface DirectoryEntry { userId: string; name: string }

const MSG_KEY = 'notify-messages'
const ANN_KEY = 'notify-announcements'

export default function NotifyPage() {
  const perm = usePerm()
  return (
    <>
      <PageHeader title="消息中心" description="站内信只看当前登录人；公告的受众由发布方算好再提交，通知服务不解析组织树。" />
      <Tabs items={[
        { key: 'inbox', label: '站内信', children: <InboxTab /> },
        perm.has(PERM.ANNOUNCE_READ) ? { key: 'announce', label: '公告', children: <AnnounceTab /> } : null,
      ].filter(Boolean) as { key: string; label: string; children: ReactNode }[]} />
    </>
  )
}

function useAction() {
  const { message } = App.useApp()
  const elevate = useElevationFlow()
  return {
    message, elevate,
    onErr: (e: unknown) => {
      const n = normalizeError(e)
      if (n.kind === 'needElevation') { void elevate.request(n); return }
      message.error(n.text)
    },
  }
}

function InboxTab() {
  const nav = useNavigate()
  const qc = useQueryClient()
  const permVersion = usePermVersion()
  const { onErr, message, elevate } = useAction()
  const [unreadOnly, setUnreadOnly] = useState(false)

  const unread = useQuery({
    queryKey: ['notify-unread', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/notify/messages/unread-count')).data.data as { unread: number },
  })
  const list = useQuery({
    queryKey: [MSG_KEY, unreadOnly, permVersion],
    queryFn: async () => (await apiClient.get(`/api/v1/notify/messages?unreadOnly=${unreadOnly}&limit=50`)).data.data as NotificationView[],
  })

  const mark = useMutation({
    mutationFn: async (id: number) => apiClient.post(`/api/v1/notify/messages/${id}/read`),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: [MSG_KEY] })
      void qc.invalidateQueries({ queryKey: ['notify-unread'] })
    },
    onError: onErr,
  })
  const markAll = useMutation({
    mutationFn: async () => apiClient.post('/api/v1/notify/messages/read-all'),
    onSuccess: (resp) => {
      message.success(`已读 ${resp.data.data?.updated ?? 0} 条`)
      void qc.invalidateQueries({ queryKey: [MSG_KEY] })
      void qc.invalidateQueries({ queryKey: ['notify-unread'] })
    },
    onError: onErr,
  })

  const cols: ColumnsType<NotificationView> = [
    {
      title: '标题', dataIndex: 'title',
      render: (v, row) => <Space>{!row.read && <Badge status="processing" />}<span>{v}</span></Space>,
    },
    { title: '分类', dataIndex: 'category', width: 120, render: (v) => <Tag>{v}</Tag> },
    { title: '时间', dataIndex: 'createdAt', width: 180, render: (v: string) => new Date(v).toLocaleString('zh-CN') },
    {
      title: '操作', key: 'op', width: 160,
      render: (_, row) => (
        <Space>
          {!row.read && <Button type="link" size="small" onClick={() => mark.mutate(row.id)}>标为已读</Button>}
          {row.link?.startsWith('/') && (
            <Button type="link" size="small" onClick={() => {
              if (!row.read) mark.mutate(row.id)
              nav(row.link!)
            }}>打开</Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <Space style={{ marginBottom: 12 }}>
        <Button type={unreadOnly ? 'primary' : 'default'} onClick={() => setUnreadOnly((v) => !v)}>
          {unreadOnly ? '只看未读' : '全部'} · 未读 {unread.data?.unread ?? 0}
        </Button>
        <Button onClick={() => markAll.mutate()} loading={markAll.isPending}>全部已读</Button>
      </Space>
      <DataCard query={list} data={list.data} emptyText={unreadOnly ? '没有未读消息' : '暂无站内信'}>
        {(rows) => <Table rowKey="id" columns={cols} dataSource={rows} pagination={{ pageSize: 15 }} size="middle" />}
      </DataCard>
      {elevate.dialog}
    </>
  )
}

function AnnounceTab() {
  const qc = useQueryClient()
  const perm = usePerm()
  const permVersion = usePermVersion()
  const { onErr, message, elevate } = useAction()
  const [open, setOpen] = useState(false)
  const [selected, setSelected] = useState<AnnouncementView | null>(null)
  const [statsId, setStatsId] = useState<number | null>(null)
  const [form] = Form.useForm()

  const list = useQuery({
    queryKey: [ANN_KEY, permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/announcements?limit=50')).data.data as AnnouncementView[],
  })
  const directory = useQuery({
    queryKey: ['notify-directory', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/org/directory?limit=200')).data.data as DirectoryEntry[],
    enabled: open && perm.has(PERM.EMPLOYEE_VIEW),
  })
  const stats = useQuery({
    queryKey: ['announce-stats', statsId, permVersion],
    queryFn: async () => (await apiClient.get(`/api/v1/announcements/${statsId}/stats`)).data.data as ReadStats,
    enabled: statsId != null && perm.has(PERM.ANNOUNCE_STATS),
  })

  const publish = useMutation({
    mutationFn: async (v: { title: string; content: string; recipients: string[] }) =>
      apiClient.post('/api/v1/announcements', v),
    onSuccess: () => { message.success('公告已发布'); setOpen(false); form.resetFields(); void qc.invalidateQueries({ queryKey: [ANN_KEY] }) },
    onError: onErr,
  })
  const read = useMutation({
    mutationFn: async (id: number) => apiClient.post(`/api/v1/announcements/${id}/read`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: [ANN_KEY] }),
    onError: onErr,
  })
  const revoke = useMutation({
    mutationFn: async (id: number) => apiClient.post(`/api/v1/announcements/${id}/revoke`),
    onSuccess: () => { message.success('已撤回'); void qc.invalidateQueries({ queryKey: [ANN_KEY] }) },
    onError: onErr,
  })

  const cols: ColumnsType<AnnouncementView> = [
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '发布人', dataIndex: 'publisherName', width: 120 },
    { title: '受众', dataIndex: 'audienceCount', width: 80 },
    { title: '状态', dataIndex: 'status', width: 110, render: (v: string) => <Tag>{v}</Tag> },
    {
      title: '我已读', dataIndex: 'readByMe', width: 90,
      render: (v: boolean | null) => (v ? <Tag color="success">是</Tag> : <Tag>否</Tag>),
    },
    {
      title: '操作', key: 'op', width: 220,
      render: (_, row) => (
        <Space>
          <Button type="link" size="small" onClick={() => {
            setSelected(row)
            if (!row.readByMe) read.mutate(row.id)
          }}>查看</Button>
          <Can code={PERM.ANNOUNCE_STATS}>
            <Button type="link" size="small" onClick={() => setStatsId(row.id)}>统计</Button>
          </Can>
          {row.status === 'PUBLISHED' && (
            <Can code={PERM.ANNOUNCE_REVOKE}>
              <Button type="link" size="small" danger onClick={() => revoke.mutate(row.id)}>撤回</Button>
            </Can>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <Can code={PERM.ANNOUNCE_PUBLISH}>
        <Button type="primary" style={{ marginBottom: 12 }} onClick={() => {
          form.setFieldValue('recipients', perm.userId ? [perm.userId] : [])
          setOpen(true)
        }}>发布公告</Button>
      </Can>
      <DataCard query={list} data={list.data} emptyText="暂无公告">
        {(rows) => <Table rowKey="id" columns={cols} dataSource={rows} pagination={{ pageSize: 15 }} size="middle" />}
      </DataCard>

      <Drawer title={selected?.title} width={520} open={selected != null} onClose={() => setSelected(null)} destroyOnClose>
        {selected && <div style={{ whiteSpace: 'pre-wrap' }}>{selected.content}</div>}
      </Drawer>
      <Drawer title="阅读统计" width={420} open={statsId != null} onClose={() => setStatsId(null)} destroyOnClose>
        {stats.data && (
          <Descriptions column={1} bordered size="small" items={[
            { key: 'aud', label: '受众', children: stats.data.audienceCount },
            { key: 'read', label: '已读', children: stats.data.readCount },
            { key: 'unread', label: '未读', children: stats.data.unreadCount },
            { key: 'bytes', label: '位图字节', children: stats.data.bitmapBytes },
          ]} />
        )}
      </Drawer>
      <Modal title="发布公告" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} confirmLoading={publish.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => publish.mutate(v)}>
          <Form.Item name="title" label="标题" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="content" label="正文" rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={4} /></Form.Item>
          <Form.Item name="recipients" label="接收人" rules={[{ required: true, type: 'array', min: 1, message: '至少一名接收人' }]}>
            <Select mode="multiple" placeholder="选择接收人" options={[
              ...(perm.userId ? [{ value: perm.userId, label: `我（${perm.userId}）` }] : []),
              ...(directory.data ?? []).filter((e) => e.userId !== perm.userId).map((e) => ({
                value: e.userId, label: `${e.name}（${e.userId}）`,
              })),
            ]} />
          </Form.Item>
        </Form>
      </Modal>
      {elevate.dialog}
    </>
  )
}
