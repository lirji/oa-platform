import { useState } from 'react'
import { App, Button, Space, Table, Tabs, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText, normalizeError } from '@oa/shared/api/errors'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataCard } from '../../components/common/DataCard'
import { usePerm, usePermVersion } from '../../auth/usePerm'
import { useElevationFlow } from '../../auth/useElevationFlow'

interface Todo {
  id: number; taskId: string; bizType: string | null; title: string | null
  summary: string | null; applicantName: string | null; assigneeUserId: string
  state: string; createdAt: string
}
interface MyApplication {
  bizType: string; docNo: string; status: string; title: string
  summary: string | null; amount: number | null; days: number | null; createdAt: string
}

const TODO_KEY = 'workbench-todos'
const MINE_KEY = 'workbench-mine'

export default function WorkbenchPage() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const perm = usePerm()
  const permVersion = usePermVersion()
  const elevate = useElevationFlow()
  const [tab, setTab] = useState('todo')

  const todos = useQuery({
    queryKey: [TODO_KEY, permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/flow/todos?limit=50')).data.data as Todo[],
  })
  const mine = useQuery({
    queryKey: [MINE_KEY, permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/flow/todos/mine?limit=50')).data.data as MyApplication[],
    enabled: tab === 'mine',
  })

  const complete = useMutation({
    mutationFn: async (v: { taskId: string; outcome: 'APPROVE' | 'REJECT' }) =>
      apiClient.post(`/api/v1/flow/todos/${v.taskId}/complete`, { outcome: v.outcome }),
    onSuccess: () => {
      // 「已受理」而不是「已完成」：办理是同步的，但业务落地经事件链路最终一致。
      // 家族的诚实文案纪律：202 语义一律说"已受理"。
      message.success('已办理')
      qc.invalidateQueries({ queryKey: [TODO_KEY] })
      qc.invalidateQueries({ queryKey: [MINE_KEY] })
    },
    onError: (e) => {
      const n = normalizeError(e)
      if (n.kind === 'needElevation') { void elevate.request(n); return }
      message.error(n.text)
    },
  })

  const todoCols: ColumnsType<Todo> = [
    { title: '标题', dataIndex: 'title', width: 260, ellipsis: true },
    { title: '类型', dataIndex: 'bizType', width: 110, render: (v: string) => <Tag>{v ?? '-'}</Tag> },
    { title: '申请人', dataIndex: 'applicantName', width: 120, render: (v) => v ?? '-' },
    { title: '摘要', dataIndex: 'summary', ellipsis: true, render: (v) => v ?? '-' },
    {
      title: '提交时间', dataIndex: 'createdAt', width: 180,
      render: (v: string) => (v ? new Date(v).toLocaleString('zh-CN') : '-'),
    },
    {
      title: '操作', key: 'op', width: 160, fixed: 'right',
      render: (_, r) => (
        <Space>
          <Button type="link" size="small" loading={complete.isPending}
            onClick={() => complete.mutate({ taskId: r.taskId, outcome: 'APPROVE' })}>同意</Button>
          <Button type="link" size="small" danger loading={complete.isPending}
            onClick={() => complete.mutate({ taskId: r.taskId, outcome: 'REJECT' })}>驳回</Button>
        </Space>
      ),
    },
  ]

  const mineCols: ColumnsType<MyApplication> = [
    { title: '单号', dataIndex: 'docNo', width: 200, render: (v) => <span className="mono">{v}</span> },
    { title: '类型', dataIndex: 'bizType', width: 110, render: (v: string) => <Tag>{v}</Tag> },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    {
      title: '金额/天数', key: 'q', width: 120,
      render: (_, r) => (r.amount != null ? `¥${r.amount}` : r.days != null ? `${r.days} 天` : '-'),
    },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (v: string) => (
        <Tag color={v === 'APPROVED' ? 'success' : v === 'REJECTED' ? 'error' : 'processing'}>{v}</Tag>
      ),
    },
    {
      title: '提交时间', dataIndex: 'createdAt', width: 180,
      render: (v: string) => (v ? new Date(v).toLocaleString('zh-CN') : '-'),
    },
  ]

  return (
    <>
      <PageHeader
        title="工作台"
        description={
          <Space size={4}>
            <Typography.Text type="secondary">
              待办 {todos.data?.length ?? 0} 条
            </Typography.Text>
            {perm.delegators.length > 0 && <Tag color="blue">含代理待办</Tag>}
          </Space>
        }
      />
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: 'todo', label: '我的待办',
            children: (
              <DataCard query={todos} data={todos.data} emptyText="暂无待办">
                {(rows) => (
                  <Table rowKey="taskId" columns={todoCols} dataSource={rows}
                    pagination={{ pageSize: 10 }} scroll={{ x: 980 }} size="middle" />
                )}
              </DataCard>
            ),
          },
          {
            key: 'mine', label: '我发起的',
            children: (
              <DataCard query={mine} data={mine.data} emptyText="你还没有提交过单据">
                {(rows) => (
                  <Table rowKey="docNo" columns={mineCols} dataSource={rows}
                    pagination={{ pageSize: 10 }} scroll={{ x: 900 }} size="middle" />
                )}
              </DataCard>
            ),
          },
        ]}
      />
      {elevate.dialog}
    </>
  )
}
