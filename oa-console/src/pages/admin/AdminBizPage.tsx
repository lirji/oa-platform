import { useEffect, useState, type ReactNode } from 'react'
import { App, Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tabs, Tag } from 'antd'
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

type Room = components['schemas']['RoomView']
type Booking = components['schemas']['BookingView']
type Asset = components['schemas']['AssetView']
type Supply = components['schemas']['SupplyView']
type Visitor = components['schemas']['VisitorView']
type Vehicle = { id?: number; plateNo?: string; plate_no?: string; model?: string; seats?: number }

function localToIso(local: string) {
  return local ? new Date(local).toISOString() : ''
}

function fmt(v?: string | null) {
  return v ? new Date(v).toLocaleString('zh-CN') : '—'
}

export default function AdminBizPage() {
  const perm = usePerm()
  const items = [
    perm.has(PERM.ROOM_BOOK) ? { key: 'rooms', label: '会议室', children: <RoomsTab /> } : null,
    { key: 'assets', label: '资产', children: <AssetsTab /> },
    perm.has(PERM.SUPPLY_REQUEST) ? { key: 'supplies', label: '用品', children: <SuppliesTab /> } : null,
    perm.has(PERM.VEHICLE_BOOK) ? { key: 'vehicles', label: '车辆', children: <VehiclesTab /> } : null,
    perm.has(PERM.VISITOR_INVITE) ? { key: 'visitors', label: '访客', children: <VisitorsTab /> } : null,
  ].filter(Boolean) as { key: string; label: string; children: ReactNode }[]

  return (
    <>
      <PageHeader title="行政" description="会议室、资产、用品、车辆与访客。重叠预定由数据库排他约束拦截，不在页面上猜。" />
      <Tabs items={items} />
    </>
  )
}

function useAction() {
  const { message } = App.useApp()
  const elevate = useElevationFlow()
  return {
    elevate,
    onErr: (e: unknown) => {
      const n = normalizeError(e)
      if (n.kind === 'needElevation') { void elevate.request(n); return }
      message.error(n.text)
    },
    message,
  }
}

function RoomsTab() {
  const qc = useQueryClient()
  const permVersion = usePermVersion()
  const { onErr, message, elevate } = useAction()
  const [roomId, setRoomId] = useState<number | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()
  const from = new Date(); from.setHours(0, 0, 0, 0)
  const to = new Date(from); to.setDate(to.getDate() + 7)

  const rooms = useQuery({
    queryKey: ['admin-rooms', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/admin-biz/rooms')).data.data as Room[],
  })
  useEffect(() => {
    if (roomId == null && rooms.data?.[0]?.id != null) setRoomId(rooms.data[0].id)
  }, [rooms.data, roomId])

  const bookings = useQuery({
    queryKey: ['admin-room-bookings', roomId, permVersion],
    queryFn: async () => (await apiClient.get(
      `/api/v1/admin-biz/rooms/${roomId}/bookings?from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`,
    )).data.data as Booking[],
    enabled: roomId != null,
  })

  const book = useMutation({
    mutationFn: async (v: { roomId: number; subject: string; startAt: string; endAt: string; attendees: number }) =>
      apiClient.post('/api/v1/admin-biz/rooms/bookings', {
        roomId: v.roomId, subject: v.subject, attendees: v.attendees,
        startAt: localToIso(v.startAt), endAt: localToIso(v.endAt),
      }),
    onSuccess: () => { message.success('已预定'); setOpen(false); form.resetFields(); void qc.invalidateQueries({ queryKey: ['admin-room-bookings'] }) },
    onError: onErr,
  })
  const cancel = useMutation({
    mutationFn: async (id: number) => apiClient.post(`/api/v1/admin-biz/rooms/bookings/${id}/cancel`),
    onSuccess: () => { message.success('已取消'); void qc.invalidateQueries({ queryKey: ['admin-room-bookings'] }) },
    onError: onErr,
  })

  const cols: ColumnsType<Booking> = [
    { title: '主题', dataIndex: 'subject', ellipsis: true },
    { title: '预定人', dataIndex: 'bookerName', width: 120 },
    { title: '开始', dataIndex: 'startAt', width: 180, render: fmt },
    { title: '结束', dataIndex: 'endAt', width: 180, render: fmt },
    { title: '状态', dataIndex: 'status', width: 100, render: (v: string) => <Tag>{v}</Tag> },
    {
      title: '操作', key: 'op', width: 90,
      render: (_, row) => row.status === 'BOOKED' && row.id != null ? (
        <Button type="link" size="small" danger loading={cancel.isPending} onClick={() => cancel.mutate(row.id!)}>取消</Button>
      ) : null,
    },
  ]

  return (
    <>
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          style={{ minWidth: 240 }}
          value={roomId ?? undefined}
          onChange={setRoomId}
          options={(rooms.data ?? []).map((r) => ({
            value: r.id, label: `${r.name ?? r.code}（${r.location ?? ''} · ${r.capacity ?? 0}人）`,
          }))}
        />
        <Can code={PERM.ROOM_BOOK}>
          <Button type="primary" onClick={() => { form.setFieldValue('roomId', roomId); setOpen(true) }}>预定</Button>
        </Can>
      </Space>
      <DataCard query={bookings} data={bookings.data} emptyText="未来 7 天没有预定">
        {(rows) => <Table rowKey="id" columns={cols} dataSource={rows} pagination={false} size="middle" scroll={{ x: 860 }} />}
      </DataCard>
      <Modal title="预定会议室" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} confirmLoading={book.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => book.mutate(v)}>
          <Form.Item name="roomId" label="会议室" rules={[{ required: true }]}>
            <Select options={(rooms.data ?? []).map((r) => ({ value: r.id, label: r.name }))} />
          </Form.Item>
          <Form.Item name="subject" label="主题" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="startAt" label="开始" rules={[{ required: true }]}><Input type="datetime-local" /></Form.Item>
          <Form.Item name="endAt" label="结束" rules={[{ required: true }]}><Input type="datetime-local" /></Form.Item>
          <Form.Item name="attendees" label="人数" rules={[{ required: true }]} initialValue={4}><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
        </Form>
      </Modal>
      {elevate.dialog}
    </>
  )
}

function AssetsTab() {
  const qc = useQueryClient()
  const permVersion = usePermVersion()
  const { onErr, message, elevate } = useAction()
  const [status, setStatus] = useState<string | undefined>()
  const assets = useQuery({
    queryKey: ['admin-assets', status, permVersion],
    queryFn: async () => {
      const q = new URLSearchParams({ limit: '200' })
      if (status) q.set('status', status)
      return (await apiClient.get(`/api/v1/admin-biz/assets?${q}`)).data.data as Asset[]
    },
  })
  const claim = useMutation({
    mutationFn: async (id: number) => apiClient.post('/api/v1/admin-biz/assets/claim', { assetId: id, remark: '控制台领用' }),
    onSuccess: () => { message.success('已领用'); void qc.invalidateQueries({ queryKey: ['admin-assets'] }) },
    onError: onErr,
  })
  const giveBack = useMutation({
    mutationFn: async (id: number) => apiClient.post(`/api/v1/admin-biz/assets/${id}/return`),
    onSuccess: () => { message.success('已归还'); void qc.invalidateQueries({ queryKey: ['admin-assets'] }) },
    onError: onErr,
  })
  const cols: ColumnsType<Asset> = [
    { title: '资产编号', dataIndex: 'assetNo', width: 160 },
    { title: '名称', dataIndex: 'name' },
    { title: '类别', dataIndex: 'category', width: 120 },
    { title: '状态', dataIndex: 'status', width: 110, render: (v: string) => <Tag>{v}</Tag> },
    { title: '持有人', dataIndex: 'holderId', width: 220, render: (v) => v ?? '—' },
    {
      title: '操作', key: 'op', width: 120,
      render: (_, row) => (
        <Can code={PERM.ASSET_CLAIM}>
          {row.status === 'IDLE' && row.id != null && (
            <Button type="link" size="small" loading={claim.isPending} onClick={() => claim.mutate(row.id!)}>领用</Button>
          )}
          {row.status === 'IN_USE' && row.id != null && (
            <Button type="link" size="small" loading={giveBack.isPending} onClick={() => giveBack.mutate(row.id!)}>归还</Button>
          )}
        </Can>
      ),
    },
  ]
  return (
    <>
      <Select allowClear placeholder="状态" style={{ width: 160, marginBottom: 12 }} value={status} onChange={setStatus}
        options={['IDLE', 'IN_USE', 'REPAIR', 'RETIRED'].map((v) => ({ value: v, label: v }))} />
      <DataCard query={assets} data={assets.data} scoped scopeModule="admin" emptyText="暂无资产">
        {(rows) => <Table rowKey="id" columns={cols} dataSource={rows} pagination={{ pageSize: 15 }} size="middle" />}
      </DataCard>
      {elevate.dialog}
    </>
  )
}

function SuppliesTab() {
  const qc = useQueryClient()
  const permVersion = usePermVersion()
  const { onErr, message, elevate } = useAction()
  const [qty, setQty] = useState<Record<number, number>>({})
  const supplies = useQuery({
    queryKey: ['admin-supplies', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/admin-biz/supplies')).data.data as Supply[],
  })
  const request = useMutation({
    mutationFn: async (v: { supplyId: number; qty: number }) => apiClient.post('/api/v1/admin-biz/supplies/requests', v),
    onSuccess: () => { message.success('申领已受理'); void qc.invalidateQueries({ queryKey: ['admin-supplies'] }) },
    onError: onErr,
  })
  const cols: ColumnsType<Supply> = [
    { title: '编码', dataIndex: 'code', width: 120 },
    { title: '名称', dataIndex: 'name' },
    { title: '单位', dataIndex: 'unit', width: 80 },
    { title: '库存', dataIndex: 'stock', width: 90 },
    {
      title: '申领', key: 'op', width: 220,
      render: (_, row) => row.id != null && (
        <Space>
          <InputNumber min={1} value={qty[row.id] ?? 1} onChange={(v) => setQty((s) => ({ ...s, [row.id!]: Number(v) || 1 }))} />
          <Button size="small" loading={request.isPending} onClick={() => request.mutate({ supplyId: row.id!, qty: qty[row.id!] ?? 1 })}>
            申领
          </Button>
        </Space>
      ),
    },
  ]
  return (
    <>
      <DataCard query={supplies} data={supplies.data} emptyText="暂无用品目录">
        {(rows) => <Table rowKey="id" columns={cols} dataSource={rows} pagination={false} size="middle" />}
      </DataCard>
      {elevate.dialog}
    </>
  )
}

function VehiclesTab() {
  const qc = useQueryClient()
  const permVersion = usePermVersion()
  const { onErr, message, elevate } = useAction()
  const [open, setOpen] = useState<Vehicle | null>(null)
  const [form] = Form.useForm()
  const vehicles = useQuery({
    queryKey: ['admin-vehicles', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/admin-biz/vehicles')).data.data as Vehicle[],
  })
  const book = useMutation({
    mutationFn: async (v: { vehicleId: number; purpose: string; startAt: string; endAt: string }) =>
      apiClient.post('/api/v1/admin-biz/vehicles/bookings', {
        vehicleId: v.vehicleId, purpose: v.purpose, startAt: localToIso(v.startAt), endAt: localToIso(v.endAt),
      }),
    onSuccess: () => { message.success('车辆已预定'); setOpen(null); form.resetFields(); void qc.invalidateQueries({ queryKey: ['admin-vehicles'] }) },
    onError: onErr,
  })
  const cols: ColumnsType<Vehicle> = [
    { title: '车牌', render: (_, r) => r.plateNo ?? r.plate_no },
    { title: '型号', dataIndex: 'model' },
    { title: '座位', dataIndex: 'seats', width: 80 },
    {
      title: '操作', key: 'op', width: 90,
      render: (_, row) => <Button type="link" size="small" onClick={() => { form.setFieldValue('vehicleId', row.id); setOpen(row) }}>预定</Button>,
    },
  ]
  return (
    <>
      <DataCard query={vehicles} data={vehicles.data} emptyText="暂无可用车辆">
        {(rows) => <Table rowKey="id" columns={cols} dataSource={rows} pagination={false} size="middle" />}
      </DataCard>
      <Modal title="预定车辆" open={open != null} onCancel={() => setOpen(null)} onOk={() => form.submit()} confirmLoading={book.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => book.mutate(v)}>
          <Form.Item name="vehicleId" hidden><InputNumber /></Form.Item>
          <Form.Item name="purpose" label="事由" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="startAt" label="开始" rules={[{ required: true }]}><Input type="datetime-local" /></Form.Item>
          <Form.Item name="endAt" label="结束" rules={[{ required: true }]}><Input type="datetime-local" /></Form.Item>
        </Form>
      </Modal>
      {elevate.dialog}
    </>
  )
}

function VisitorsTab() {
  const qc = useQueryClient()
  const permVersion = usePermVersion()
  const { onErr, message, elevate } = useAction()
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()
  const visitors = useQuery({
    queryKey: ['admin-visitors', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/admin-biz/visitors?limit=50')).data.data as Visitor[],
  })
  const invite = useMutation({
    mutationFn: async (v: { name: string; phone?: string; company?: string; visitAt: string }) =>
      apiClient.post('/api/v1/admin-biz/visitors', { ...v, visitAt: localToIso(v.visitAt) }),
    onSuccess: () => { message.success('已邀请'); setOpen(false); form.resetFields(); void qc.invalidateQueries({ queryKey: ['admin-visitors'] }) },
    onError: onErr,
  })
  const checkIn = useMutation({
    mutationFn: async (id: number) => apiClient.post(`/api/v1/admin-biz/visitors/${id}/check-in`),
    onSuccess: () => { message.success('已签到'); void qc.invalidateQueries({ queryKey: ['admin-visitors'] }) },
    onError: onErr,
  })
  const checkOut = useMutation({
    mutationFn: async (id: number) => apiClient.post(`/api/v1/admin-biz/visitors/${id}/check-out`),
    onSuccess: () => { message.success('已签离'); void qc.invalidateQueries({ queryKey: ['admin-visitors'] }) },
    onError: onErr,
  })
  const cols: ColumnsType<Visitor> = [
    { title: '姓名', dataIndex: 'name' },
    { title: '单位', dataIndex: 'company', render: (v) => v ?? '—' },
    { title: '到访时间', dataIndex: 'visitAt', width: 180, render: fmt },
    { title: '状态', dataIndex: 'status', width: 120, render: (v: string) => <Tag>{v}</Tag> },
    {
      title: '操作', key: 'op', width: 160,
      render: (_, row) => row.id != null && (
        <Can code={PERM.VISITOR_MANAGE}>
          {row.status === 'BOOKED' || row.status === 'INVITED' ? (
            <Button type="link" size="small" loading={checkIn.isPending} onClick={() => checkIn.mutate(row.id!)}>签到</Button>
          ) : null}
          {row.status === 'CHECKED_IN' ? (
            <Button type="link" size="small" loading={checkOut.isPending} onClick={() => checkOut.mutate(row.id!)}>签离</Button>
          ) : null}
        </Can>
      ),
    },
  ]
  return (
    <>
      <Button type="primary" style={{ marginBottom: 12 }} onClick={() => setOpen(true)}>邀请访客</Button>
      <DataCard query={visitors} data={visitors.data} emptyText="暂无访客">
        {(rows) => <Table rowKey="id" columns={cols} dataSource={rows} pagination={{ pageSize: 15 }} size="middle" />}
      </DataCard>
      <Modal title="邀请访客" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} confirmLoading={invite.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => invite.mutate(v)}>
          <Form.Item name="name" label="姓名" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="company" label="单位"><Input /></Form.Item>
          <Form.Item name="phone" label="手机"><Input /></Form.Item>
          <Form.Item name="visitAt" label="到访时间" rules={[{ required: true }]}><Input type="datetime-local" /></Form.Item>
        </Form>
      </Modal>
      {elevate.dialog}
    </>
  )
}
