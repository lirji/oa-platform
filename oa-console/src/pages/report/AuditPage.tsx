import { useState } from 'react'
import { App, Button, Card, Form, Input, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { normalizeError } from '@oa/shared/api/errors'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataCard } from '../../components/common/DataCard'
import { useElevationFlow } from '../../auth/useElevationFlow'
import { useAppBreakpoint } from '../../hooks/useAppBreakpoint'

interface AuditRow {
  id: number; actorId: string | null; actorName: string | null; onBehalfOf: string | null
  action: string; module: string; outcome: string; error: string | null; createdAt: string
}

/**
 * 审计查询。
 *
 * <p>★ 这一页是 JIT 提权最好的落地场景：`oa:audit:view` 带 `require_elevation`，
 * 持 SUPER_ADMIN 直接进也会 3002 —— 让"我现在要查审计"成为一个**有记录、有时限**的动作。
 */
export default function AuditPage() {
  const { message } = App.useApp()
  const elevate = useElevationFlow()
  const [filters, setFilters] = useState<{ actorId?: string; deniedOnly?: boolean }>({})
  // C 档起一页 10 条：1366×768 下可用高度只剩 ~620px，20 条要滚两屏才够得着分页器。
  const bp = useAppBreakpoint()

  const q = useQuery({
    queryKey: ['audit', filters],
    queryFn: async () => {
      const p = new URLSearchParams({ limit: '100' })
      if (filters.actorId) p.set('actorId', filters.actorId)
      if (filters.deniedOnly) p.set('deniedOnly', 'true')
      try {
        return (await apiClient.get(`/api/v1/report/audit?${p}`)).data.data as AuditRow[]
      } catch (e) {
        const n = normalizeError(e)
        // ★ 3002 走提权闭环并【重放】—— 当成普通 403 的话用户永远找不到申请入口
        if (n.kind === 'needElevation') { elevate.request(n, () => void q.refetch()); }
        throw e
      }
    },
    retry: false,
  })

  const cols: ColumnsType<AuditRow> = [
    {
      title: '时间', dataIndex: 'createdAt', width: 180,
      render: (v: string) => new Date(v).toLocaleString('zh-CN'),
    },
    { title: '操作人', dataIndex: 'actorName', width: 130, render: (_, r) => r.actorName ?? r.actorId ?? '-' },
    {
      title: '代理', dataIndex: 'onBehalfOf', width: 120,
      render: (v: string | null) => (v ? <Tag color="blue">代 {v}</Tag> : '-'),
    },
    { title: '动作', dataIndex: 'action', width: 220, render: (v) => <span className="mono">{v}</span> },
    { title: '模块', dataIndex: 'module', width: 100 },
    {
      title: '结果', dataIndex: 'outcome', width: 100,
      render: (v: string) => (
        <Tag color={v === 'SUCCESS' ? 'success' : v === 'DENIED' ? 'error' : 'warning'}>{v}</Tag>
      ),
    },
    { title: '错误', dataIndex: 'error', ellipsis: true, render: (v) => v ?? '-' },
  ]

  return (
    <>
      <PageHeader
        title="审计日志"
        description="查审计需要临时提权 —— 它是权限最高的一类只读数据"
      />
      <Card size="small" className="filter-card" style={{ marginBottom: 12 }}>
        <Form layout="inline" onFinish={(v) => setFilters(v)}>
          <Form.Item name="actorId" label="操作人">
            <Input allowClear placeholder="userId" style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="deniedOnly" label="只看被拒" initialValue={false}>
            <Select style={{ width: 120 }} options={[
              { value: false, label: '全部' }, { value: true, label: '仅 DENIED' },
            ]} />
          </Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">筛选</Button>
            <Button onClick={() => setFilters({})}>重置</Button>
          </Space>
        </Form>
      </Card>
      <DataCard query={q} data={q.data} emptyText="没有匹配的审计记录">
        {(rows) => <Table rowKey="id" columns={cols} dataSource={rows}
          pagination={{ pageSize: bp.tablePageSize }} scroll={{ x: 1080 }} size="middle" />}
      </DataCard>
      {elevate.dialog}
    </>
  )
}
