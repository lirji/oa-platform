import { useMemo, useState } from 'react'
import { App, Button, Card, Col, Collapse, Input, Row, Space, Statistic, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import type { components } from '@oa/shared/types/openapi'
import { normalizeError } from '@oa/shared/api/errors'
import { PERM } from '@oa/shared/perm/codes'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataCard } from '../../components/common/DataCard'
import Can from '../../auth/Can'
import { usePerm, usePermVersion } from '../../auth/usePerm'
import { useElevationFlow } from '../../auth/useElevationFlow'

type PunchRow = { punchType?: string; punchTime?: string }
type PunchResult = components['schemas']['PunchResult']
type Pipeline = {
  accepted?: number; duplicated?: number; persisted?: number
  queueDepth?: number; redisAvailable?: boolean
}
type Storage = { punchCount?: number; duplicates?: number; deadLetters?: number }

const ME_KEY = 'attendance-me'
const STATS_KEY = 'attendance-stats'

function today() {
  const now = new Date()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${m}-${d}`
}

function clock(v?: string) {
  if (!v) return '—'
  return new Date(v).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function workLabel(minutes: number | null) {
  if (minutes == null || minutes < 0) return '—'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h} 小时 ${m} 分` : `${m} 分钟`
}

export default function AttendancePage() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const perm = usePerm()
  const permVersion = usePermVersion()
  const elevate = useElevationFlow()
  const [day, setDay] = useState(today)
  const isToday = day === today()

  const punches = useQuery({
    queryKey: [ME_KEY, day, permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/attendance/me', { params: { date: day } })).data.data as PunchRow[],
  })

  const stats = useQuery({
    queryKey: [STATS_KEY, day, permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/attendance/admin/stats', { params: { date: day } })).data.data as {
      pipeline?: Pipeline; storage?: Storage
    },
    enabled: perm.has(PERM.ATTENDANCE_ADMIN),
  })

  const punch = useMutation({
    mutationFn: async (type: 'IN' | 'OUT') =>
      (await apiClient.post('/api/v1/attendance/punch', null, { params: { type, source: 'WEB' } })).data.data as PunchResult,
    onSuccess: (data) => {
      message.success(data.message ?? (data.duplicate ? '重复打卡已忽略' : '打卡已受理'))
      void qc.invalidateQueries({ queryKey: [ME_KEY] })
      void qc.invalidateQueries({ queryKey: [STATS_KEY] })
    },
    onError: (e) => {
      const n = normalizeError(e)
      if (n.kind === 'needElevation') { void elevate.request(n); return }
      message.error(n.text)
    },
  })

  const compute = useMutation({
    mutationFn: async () => (await apiClient.post('/api/v1/attendance/daily-compute', null, { params: { date: day } })).data.data as {
      employees?: number; elapsedMs?: number; byStatus?: Record<string, number>
    },
    onSuccess: (data) => {
      message.success(`日结已受理：${data.employees ?? 0} 人，${data.elapsedMs ?? 0} ms`)
      void qc.invalidateQueries({ queryKey: [STATS_KEY] })
    },
    onError: (e) => {
      const n = normalizeError(e)
      if (n.kind === 'needElevation') { void elevate.request(n); return }
      message.error(n.text)
    },
  })

  const rows = punches.data ?? []
  const firstIn = useMemo(() => rows.find((r) => r.punchType === 'IN'), [rows])
  const lastOut = useMemo(() => [...rows].reverse().find((r) => r.punchType === 'OUT'), [rows])
  const minutes = firstIn?.punchTime && lastOut?.punchTime
    ? Math.round((new Date(lastOut.punchTime).getTime() - new Date(firstIn.punchTime).getTime()) / 60000)
    : null
  const status = !firstIn ? '未打卡' : !lastOut ? '缺下班卡' : '已齐'
  const statusColor = status === '已齐' ? 'success' : status === '缺下班卡' ? 'warning' : 'default'

  const cols: ColumnsType<PunchRow> = [
    {
      title: '类型', dataIndex: 'punchType', width: 120,
      render: (v: string) => <Tag color={v === 'IN' ? 'processing' : 'default'}>{v === 'IN' ? '上班' : v === 'OUT' ? '下班' : v}</Tag>,
    },
    {
      title: '时间', dataIndex: 'punchTime',
      render: (v: string) => (v ? new Date(v).toLocaleString('zh-CN') : '—'),
    },
  ]

  const pipeline = stats.data?.pipeline
  const storage = stats.data?.storage

  return (
    <>
      <PageHeader
        title="考勤"
        description={isToday ? '打卡入队即返回，重复打卡不会再记一条。' : `正在查看 ${day}，打卡按钮只对今天生效。`}
        extra={
          <Space wrap>
            <Input type="date" value={day} onChange={(e) => setDay(e.target.value)} aria-label="考勤日期" style={{ width: 160 }} />
            <Can code={PERM.ATTENDANCE_PUNCH}>
              <Button type="primary" disabled={!isToday} loading={punch.isPending} onClick={() => punch.mutate('IN')} data-testid="primary-action">
                上班打卡
              </Button>
            </Can>
            <Can code={PERM.ATTENDANCE_PUNCH}>
              <Button disabled={!isToday} loading={punch.isPending} onClick={() => punch.mutate('OUT')}>下班打卡</Button>
            </Can>
          </Space>
        }
      />

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} xl={6}>
          <Card size="small"><Statistic title="上班" value={clock(firstIn?.punchTime)} /></Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card size="small"><Statistic title="下班" value={clock(lastOut?.punchTime)} /></Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card size="small"><Statistic title="工时" value={workLabel(minutes)} /></Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card size="small">
            <Typography.Text type="secondary">当日状态</Typography.Text>
            <div style={{ marginTop: 8 }}><Tag color={statusColor}>{status}</Tag></div>
          </Card>
        </Col>
      </Row>

      <DataCard query={punches} data={punches.data} emptyText="这一天还没有打卡">
        {(data) => <Table rowKey={(r, i) => `${r.punchType}-${r.punchTime}-${i}`} columns={cols} dataSource={data} pagination={false} size="middle" />}
      </DataCard>

      {perm.has(PERM.ATTENDANCE_ADMIN) && (
        <Collapse
          style={{ marginTop: 16 }}
          items={[{
            key: 'ops',
            label: '打卡链路（管理）',
            extra: <Button size="small" loading={compute.isPending} onClick={(e) => { e.stopPropagation(); compute.mutate() }}>跑日结</Button>,
            children: (
              <Row gutter={16}>
                <Col xs={24} sm={12} xl={6}><Statistic title="入队" value={pipeline?.accepted ?? 0} /></Col>
                <Col xs={24} sm={12} xl={6}><Statistic title="落库" value={pipeline?.persisted ?? storage?.punchCount ?? 0} /></Col>
                <Col xs={24} sm={12} xl={6}><Statistic title="队列深度" value={pipeline?.queueDepth ?? 0} /></Col>
                <Col xs={24} sm={12} xl={6}>
                  <Statistic title="Redis" value={pipeline?.redisAvailable === false ? '降级' : '可用'} />
                </Col>
              </Row>
            ),
          }]}
        />
      )}
      {elevate.dialog}
    </>
  )
}
