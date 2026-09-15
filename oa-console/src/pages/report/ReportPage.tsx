import { Card, Col, Row, Statistic, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useQuery } from '@tanstack/react-query'
import { z } from 'zod'
import { apiClient } from '@oa/shared/api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataCard } from '../../components/common/DataCard'
import { usePermVersion } from '../../auth/usePerm'
import { parseApproval, parseHeadcount, type ApprovalRow, type HeadcountRow } from './reportView'

export default function ReportPage() {
  const permVersion = usePermVersion()
  const overview = useQuery({
    queryKey: ['report-overview', permVersion],
    queryFn: async () => {
      const { data } = await apiClient.get('/api/v1/report/overview')
      const payload = z.object({
        approval: z.array(z.unknown()).optional(),
        headcountTop: z.array(z.unknown()).optional(),
      }).parse(data.data ?? {})
      return {
        approval: (payload.approval ?? []).map(parseApproval),
        headcountTop: (payload.headcountTop ?? []).map(parseHeadcount),
      }
    },
  })

  const approvalCols: ColumnsType<ApprovalRow> = [
    { title: '单据类型', dataIndex: 'bizType', width: 140, render: (v) => <Tag>{v}</Tag> },
    { title: '总数', dataIndex: 'total', width: 90 },
    { title: '已办结', dataIndex: 'finished', width: 90 },
    { title: '在办', dataIndex: 'running', width: 90 },
    { title: '驳回', dataIndex: 'rejected', width: 90 },
    {
      title: '平均耗时', dataIndex: 'avgHours', width: 120,
      render: (v: number | null) => (v == null ? '—' : `${v} 小时`),
    },
    { title: '最长耗时', dataIndex: 'maxHours', width: 120, render: (v: number | null) => (v == null ? '—' : `${v} 小时`) },
  ]

  const hcCols: ColumnsType<HeadcountRow> = [
    { title: '组织', dataIndex: 'orgName', ellipsis: true },
    { title: '路径', dataIndex: 'orgPath', width: 220, render: (v) => <span className="mono">{v}</span> },
    { title: '在岗人数', dataIndex: 'headcount', width: 110 },
  ]

  return (
    <>
      <PageHeader title="管理驾驶舱" description="只读视图，数据实时聚合" />
      <Row gutter={16} style={{ marginBottom: 16 }}>
        {(overview.data?.approval ?? []).slice(0, 4).map((a) => (
          <Col xs={24} sm={12} xl={6} key={a.bizType}>
            <Card size="small">
              <Statistic title={`${a.bizType} 在办`} value={a.running} suffix={`/ ${a.total}`} />
            </Card>
          </Col>
        ))}
      </Row>
      <Row gutter={16}>
        <Col xs={24} xl={14}>
          <DataCard title="审批时效" query={overview} data={overview.data?.approval} emptyText="暂无审批数据">
            {(rows) => <Table rowKey="bizType" columns={approvalCols} dataSource={rows}
              pagination={false} scroll={{ x: 740 }} size="middle" />}
          </DataCard>
        </Col>
        <Col xs={24} xl={10}>
          <DataCard title="编制人效 Top" query={overview} data={overview.data?.headcountTop} emptyText="暂无组织数据">
            {(rows) => <Table rowKey="orgId" columns={hcCols} dataSource={rows}
              pagination={false} scroll={{ x: 520 }} size="middle" />}
          </DataCard>
        </Col>
      </Row>
    </>
  )
}
