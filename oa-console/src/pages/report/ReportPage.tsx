import { Card, Col, Row, Statistic, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useQuery } from '@tanstack/react-query'
import { z } from 'zod'
import { apiClient } from '@oa/shared/api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataCard } from '../../components/common/DataCard'

/**
 * ★ 驾驶舱的后端返回是 `Map<String,Object>`（未类型化，见 CLAUDE.md 硬约束第 9 条的例外），
 * springdoc 只能产 `Record<string, unknown>`。所以这里**手写 schema + 运行时校验** ——
 * 校验失败直接报错，而不是让 `row.avgHours` 静默拿到 undefined 然后渲染出空白。
 */
const Approval = z.object({
  biz_type: z.string(),
  total: z.number(),
  finished: z.number(),
  running: z.number(),
  rejected: z.number(),
  avg_hours: z.number().nullable(),
  max_hours: z.number().nullable(),
})
const Headcount = z.object({
  org_id: z.number(), org_name: z.string(), org_path: z.string(), headcount: z.number(),
})
type Approval = z.infer<typeof Approval>
type Headcount = z.infer<typeof Headcount>

export default function ReportPage() {
  const overview = useQuery({
    queryKey: ['report-overview'],
    queryFn: async () => {
      const { data } = await apiClient.get('/api/v1/report/overview')
      // overview 是 Map-of-List（`{approval:[...], headcountTop:[...]}`），不是 List<Map>
      return {
        approval: z.array(Approval).parse(data.data.approval ?? []),
        headcountTop: z.array(Headcount).parse(data.data.headcountTop ?? []),
      }
    },
  })

  const approvalCols: ColumnsType<Approval> = [
    { title: '单据类型', dataIndex: 'biz_type', width: 140, render: (v) => <Tag>{v}</Tag> },
    { title: '总数', dataIndex: 'total', width: 90 },
    { title: '已办结', dataIndex: 'finished', width: 90 },
    { title: '在办', dataIndex: 'running', width: 90 },
    { title: '驳回', dataIndex: 'rejected', width: 90 },
    {
      title: '平均耗时', dataIndex: 'avg_hours', width: 120,
      // avg_hours 可为 null（没有已办结的单据时）。不处理会渲染成空白，看起来像"0 小时"
      render: (v: number | null) => (v == null ? '—' : `${v} 小时`),
    },
    { title: '最长耗时', dataIndex: 'max_hours', width: 120, render: (v: number | null) => (v == null ? '—' : `${v} 小时`) },
  ]

  const hcCols: ColumnsType<Headcount> = [
    { title: '组织', dataIndex: 'org_name', ellipsis: true },
    { title: '路径', dataIndex: 'org_path', width: 220, render: (v) => <span className="mono">{v}</span> },
    { title: '在岗人数', dataIndex: 'headcount', width: 110 },
  ]

  return (
    <>
      <PageHeader title="管理驾驶舱" description="只读视图，数据实时聚合" />
      <Row gutter={16} style={{ marginBottom: 16 }}>
        {(overview.data?.approval ?? []).slice(0, 4).map((a) => (
          <Col xs={24} sm={12} xl={6} key={a.biz_type}>
            <Card size="small">
              <Statistic title={`${a.biz_type} 在办`} value={a.running} suffix={`/ ${a.total}`} />
            </Card>
          </Col>
        ))}
      </Row>
      <Row gutter={16}>
        <Col xs={24} xl={14}>
          <DataCard title="审批时效" query={overview} data={overview.data?.approval} emptyText="暂无审批数据">
            {(rows) => <Table rowKey="biz_type" columns={approvalCols} dataSource={rows}
              pagination={false} scroll={{ x: 740 }} size="middle" />}
          </DataCard>
        </Col>
        <Col xs={24} xl={10}>
          <DataCard title="编制人效 Top" query={overview} data={overview.data?.headcountTop} emptyText="暂无组织数据">
            {(rows) => <Table rowKey="org_id" columns={hcCols} dataSource={rows}
              pagination={false} scroll={{ x: 520 }} size="middle" />}
          </DataCard>
        </Col>
      </Row>
    </>
  )
}
