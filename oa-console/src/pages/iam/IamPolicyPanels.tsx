import { useState } from 'react'
import {
  Alert, App, Button, Card, Descriptions, Drawer, Form, Input, Modal, Popconfirm,
  Select, Space, Switch, Table, Tag, Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, TeamOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'
import { DataCard } from '../../components/common/DataCard'
import { usePermVersion } from '../../auth/usePerm'

export interface UserGroup {
  id: number
  code: string
  name: string
  description: string | null
  status: 'ACTIVE' | 'DISABLED'
  updatedAt: string
}

interface GroupMember {
  id: number
  userId: string
  validFrom: string
  validTo: string | null
  createdBy: string
}

interface Role { id: number; code: string; name: string }
interface Permission { id: number; code: string; name: string; enabled: boolean }
interface Condition {
  id: number
  roleId: number
  permissionId: number
  expression: string
  description: string | null
  enabled: boolean
  updatedAt: string
}

const GROUP_KEY = 'iam-user-groups'
const CONDITION_KEY = 'iam-abac-conditions'

function isoOrNull(value?: string) {
  return value ? new Date(value).toISOString() : null
}

export function UserGroupsPanel() {
  const permVersion = usePermVersion()
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [editingGroup, setEditingGroup] = useState<UserGroup | null>(null)
  const [selected, setSelected] = useState<UserGroup | null>(null)
  const [createForm] = Form.useForm()
  const [memberForm] = Form.useForm()

  const groups = useQuery({
    queryKey: [GROUP_KEY, permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/iam/groups')).data.data as UserGroup[],
  })
  const members = useQuery({
    queryKey: ['iam-group-members', selected?.id, permVersion],
    queryFn: async () =>
      (await apiClient.get(`/api/v1/iam/groups/${selected!.id}/members`)).data.data as GroupMember[],
    enabled: Boolean(selected),
  })

  const saveGroup = useMutation({
    mutationFn: async (v: { code?: string; name: string; description?: string }) => {
      if (editingGroup) {
        await apiClient.put(`/api/v1/iam/groups/${editingGroup.id}`, { name: v.name, description: v.description })
      } else {
        await apiClient.post('/api/v1/iam/groups', v)
      }
    },
    onSuccess: async () => {
      message.success(editingGroup ? '用户组已更新' : '用户组已创建')
      setCreateOpen(false)
      setEditingGroup(null)
      createForm.resetFields()
      await qc.invalidateQueries({ queryKey: [GROUP_KEY] })
    },
    onError: (e) => message.error(errText(e)),
  })
  const toggle = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      apiClient.post(`/api/v1/iam/groups/${id}/enabled?enabled=${enabled}`),
    onSuccess: async () => {
      message.success('用户组状态已更新，相关权限快照正在失效')
      await qc.invalidateQueries({ queryKey: [GROUP_KEY] })
    },
    onError: (e) => message.error(errText(e)),
  })
  const addMembers = useMutation({
    mutationFn: (v: { userIds: string; validFrom?: string; validTo?: string }) =>
      apiClient.post(`/api/v1/iam/groups/${selected!.id}/members`, {
        userIds: v.userIds.split(/[\s,，]+/).map((x) => x.trim()).filter(Boolean),
        validFrom: isoOrNull(v.validFrom),
        validTo: isoOrNull(v.validTo),
      }),
    onSuccess: async (resp) => {
      message.success(`已写入 ${resp.data.data.affected} 名成员`)
      memberForm.resetFields()
      await qc.invalidateQueries({ queryKey: ['iam-group-members', selected?.id] })
    },
    onError: (e) => message.error(errText(e)),
  })
  const removeMember = useMutation({
    mutationFn: (userId: string) =>
      apiClient.delete(`/api/v1/iam/groups/${selected!.id}/members/${encodeURIComponent(userId)}`),
    onSuccess: async () => {
      message.success('成员已移出，相关权限快照正在失效')
      await qc.invalidateQueries({ queryKey: ['iam-group-members', selected?.id] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const columns: ColumnsType<UserGroup> = [
    { title: '编码', dataIndex: 'code', width: 180, render: (v) => <Typography.Text code>{v}</Typography.Text> },
    { title: '名称', dataIndex: 'name', width: 180 },
    { title: '说明', dataIndex: 'description', ellipsis: true, render: (v) => v || '-' },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (v) => <Tag color={v === 'ACTIVE' ? 'success' : 'default'}>{v === 'ACTIVE' ? '启用' : '禁用'}</Tag>,
    },
    {
      title: '操作', key: 'op', width: 260,
      render: (_, row) => (
        <Space>
          <Button size="small" icon={<TeamOutlined />} onClick={() => setSelected(row)}>成员</Button>
          <Button size="small" onClick={() => {
            setEditingGroup(row)
            createForm.setFieldsValue(row)
            setCreateOpen(true)
          }}>编辑</Button>
          <Popconfirm
            title={row.status === 'ACTIVE' ? '禁用这个用户组？' : '重新启用这个用户组？'}
            description={row.status === 'ACTIVE' ? '组授权将立即停止生效，但成员记录会保留。' : undefined}
            onConfirm={() => toggle.mutate({ id: row.id, enabled: row.status !== 'ACTIVE' })}
          >
            <Button size="small" danger={row.status === 'ACTIVE'} loading={toggle.isPending}>
              {row.status === 'ACTIVE' ? '禁用' : '启用'}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]
  const memberColumns: ColumnsType<GroupMember> = [
    { title: '用户 ID', dataIndex: 'userId', width: 170 },
    { title: '生效时间', dataIndex: 'validFrom', render: (v) => new Date(v).toLocaleString('zh-CN') },
    { title: '失效时间', dataIndex: 'validTo', render: (v) => v ? new Date(v).toLocaleString('zh-CN') : '长期' },
    {
      title: '操作', width: 80,
      render: (_, row) => (
        <Popconfirm title={`移出 ${row.userId}？`} onConfirm={() => removeMember.mutate(row.userId)}>
          <Button type="link" size="small" danger>移出</Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <>
      <DataCard
        title="用户组"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => {
          setEditingGroup(null)
          createForm.resetFields()
          setCreateOpen(true)
        }}>新建用户组</Button>}
        query={groups} data={groups.data} emptyText="还没有用户组"
      >
        {(rows) => <Table rowKey="id" columns={columns} dataSource={rows} pagination={false} scroll={{ x: 760 }} />}
      </DataCard>
      <Modal title={editingGroup ? '编辑用户组' : '新建用户组'} open={createOpen}
        onCancel={() => { setCreateOpen(false); setEditingGroup(null); createForm.resetFields() }}
        onOk={() => createForm.submit()} confirmLoading={saveGroup.isPending} destroyOnClose>
        <Form form={createForm} layout="vertical" onFinish={(v) => saveGroup.mutate(v)} preserve={false}>
          <Form.Item name="code" label="编码" extra="创建后不可修改；2–64 位字母、数字、_ 或 -。"
            rules={[{ required: true }, { pattern: /^[A-Za-z][A-Za-z0-9_-]{1,63}$/, message: '格式不正确' }]}>
            <Input disabled={Boolean(editingGroup)} placeholder="例如 FINANCE_APPROVERS" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="description" label="说明"><Input.TextArea rows={3} /></Form.Item>
        </Form>
      </Modal>
      <Drawer title={selected ? `${selected.name} · 成员` : '用户组成员'} width={720}
        open={Boolean(selected)} onClose={() => setSelected(null)} destroyOnClose>
        <Alert type="info" showIcon message="成员时间窗会参与实时判权；新增、移出或修改组状态都会使权限快照失效。" />
        <Form form={memberForm} layout="vertical" onFinish={(v) => addMembers.mutate(v)} style={{ marginTop: 16 }}>
          <Form.Item name="userIds" label="批量添加用户 ID" extra="用空格、逗号或换行分隔，单次最多 500 人。"
            rules={[{ required: true }]}>
            <Input.TextArea rows={3} placeholder={'user-001\nuser-002'} />
          </Form.Item>
          <Space wrap>
            <Form.Item name="validFrom" label="生效时间（可选）"><Input type="datetime-local" /></Form.Item>
            <Form.Item name="validTo" label="失效时间（可选）"><Input type="datetime-local" /></Form.Item>
            <Button type="primary" htmlType="submit" loading={addMembers.isPending}>添加成员</Button>
          </Space>
        </Form>
        <DataCard title="当前成员" query={members} data={members.data} emptyText="暂无成员">
          {(rows) => <Table rowKey="id" size="small" columns={memberColumns} dataSource={rows} pagination={false} />}
        </DataCard>
      </Drawer>
    </>
  )
}

export function AbacPanel() {
  const permVersion = usePermVersion()
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Condition | null>(null)
  const [form] = Form.useForm()

  const roles = useQuery({
    queryKey: ['iam-roles'],
    queryFn: async () => (await apiClient.get('/api/v1/iam/roles')).data.data as Role[],
    staleTime: 5 * 60_000,
  })
  const permissions = useQuery({
    queryKey: ['perm-catalog'],
    queryFn: async () => (await apiClient.get('/api/v1/iam/permissions/catalog')).data.data as Permission[],
    staleTime: 5 * 60_000,
  })
  const conditions = useQuery({
    queryKey: [CONDITION_KEY, permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/iam/abac/conditions')).data.data as Condition[],
  })
  const save = useMutation({
    mutationFn: async (v: Record<string, unknown>) => editing
      ? apiClient.put(`/api/v1/iam/abac/conditions/${editing.id}`, v)
      : apiClient.post('/api/v1/iam/abac/conditions', v),
    onSuccess: async () => {
      message.success(editing ? '条件已更新' : '条件已创建')
      setOpen(false)
      setEditing(null)
      form.resetFields()
      await qc.invalidateQueries({ queryKey: [CONDITION_KEY] })
    },
    onError: (e) => message.error(errText(e)),
  })
  const validate = useMutation({
    mutationFn: (expression: string) => apiClient.post('/api/v1/iam/abac/validate', { expression }),
    onSuccess: () => message.success('表达式语法有效'),
    onError: (e) => message.error(errText(e)),
  })
  const toggle = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      apiClient.post(`/api/v1/iam/abac/conditions/${id}/enabled?enabled=${enabled}`),
    onSuccess: async () => qc.invalidateQueries({ queryKey: [CONDITION_KEY] }),
    onError: (e) => message.error(errText(e)),
  })
  const remove = useMutation({
    mutationFn: (id: number) => apiClient.delete(`/api/v1/iam/abac/conditions/${id}`),
    onSuccess: async () => {
      message.success('条件已删除')
      await qc.invalidateQueries({ queryKey: [CONDITION_KEY] })
    },
    onError: (e) => message.error(errText(e)),
  })
  const roleName = (id: number) => roles.data?.find((x) => x.id === id)?.name ?? `#${id}`
  const permName = (id: number) => permissions.data?.find((x) => x.id === id)
  const edit = (row: Condition) => {
    setEditing(row)
    form.setFieldsValue(row)
    setOpen(true)
  }

  const columns: ColumnsType<Condition> = [
    { title: '角色', dataIndex: 'roleId', width: 150, render: roleName },
    {
      title: '权限点', dataIndex: 'permissionId', width: 240,
      render: (id) => {
        const p = permName(id)
        return p ? <><div>{p.name}</div><Typography.Text type="secondary" code>{p.code}</Typography.Text></> : `#${id}`
      },
    },
    { title: '条件表达式', dataIndex: 'expression', render: (v) => <Typography.Text code>{v}</Typography.Text> },
    { title: '说明', dataIndex: 'description', ellipsis: true, render: (v) => v || '-' },
    {
      title: '启用', dataIndex: 'enabled', width: 80,
      render: (enabled, row) => <Switch size="small" checked={enabled}
        loading={toggle.isPending} onChange={(next) => toggle.mutate({ id: row.id, enabled: next })} />,
    },
    {
      title: '操作', width: 120,
      render: (_, row) => <Space>
        <Button type="link" size="small" onClick={() => edit(row)}>编辑</Button>
        <Popconfirm title="删除这条 ABAC 条件？" onConfirm={() => remove.mutate(row.id)}>
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>
      </Space>,
    },
  ]

  return (
    <>
      <Alert type="warning" showIcon style={{ marginBottom: 16 }}
        message="ABAC 默认关闭；需设置 OA_IAM_ABAC_ENABLED=true 才参与判权。"
        description="同一角色权限下多条条件按 AND；不同授权来源按 OR；存在无条件授权来源时直接放行。运行时异常一律拒绝。" />
      <DataCard title="ABAC 条件"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); form.resetFields(); setOpen(true) }}>新建条件</Button>}
        query={conditions} data={conditions.data} emptyText="尚未配置 ABAC 条件">
        {(rows) => <Table rowKey="id" columns={columns} dataSource={rows} pagination={false} scroll={{ x: 980 }} />}
      </DataCard>
      <Modal title={editing ? '编辑 ABAC 条件' : '新建 ABAC 条件'} width={720} open={open}
        onCancel={() => { setOpen(false); setEditing(null) }} onOk={() => form.submit()}
        confirmLoading={save.isPending} destroyOnClose>
        <Form form={form} layout="vertical" preserve={false} initialValues={{ enabled: true }} onFinish={(v) => save.mutate(v)}>
          <Space size="large" align="start" wrap>
            <Form.Item name="roleId" label="角色" rules={[{ required: true }]}>
              <Select style={{ width: 260 }} loading={roles.isLoading} showSearch optionFilterProp="label"
                options={(roles.data ?? []).map((x) => ({ value: x.id, label: `${x.name}（${x.code}）` }))} />
            </Form.Item>
            <Form.Item name="permissionId" label="权限点" rules={[{ required: true }]}
              extra="角色必须直接或经继承拥有该权限。">
              <Select style={{ width: 340 }} loading={permissions.isLoading} showSearch optionFilterProp="label"
                options={(permissions.data ?? []).filter((x) => x.enabled)
                  .map((x) => ({ value: x.id, label: `${x.name}（${x.code}）` }))} />
            </Form.Item>
          </Space>
          <Form.Item name="expression" label="受限 SpEL 条件" rules={[{ required: true }, { max: 512 }]}
            extra="可用 #user、#args、#p0/#a0 和方法参数名；禁止类型、构造器、Bean、方法调用与赋值。">
            <Input.TextArea rows={4} placeholder="#p0.amount <= 5000 and #user.primaryOrgId == 10" />
          </Form.Item>
          <Space align="start" wrap>
            <Form.Item name="description" label="说明"><Input style={{ width: 430 }} /></Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
          </Space>
          <Button onClick={() => {
            const expression = form.getFieldValue('expression')
            if (!expression) return message.warning('请先输入表达式')
            validate.mutate(expression)
          }} loading={validate.isPending}>仅校验表达式</Button>
        </Form>
      </Modal>
    </>
  )
}
