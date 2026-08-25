import { useEffect, useMemo, useState } from 'react'
import {
  App, Button, Card, Checkbox, Col, Collapse, Drawer, Form, Input, Modal,
  Popconfirm, Row, Select, Space, Table, Tabs, Tag, Tooltip, Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'
import { DataCard } from '../../components/common/DataCard'
import { usePermVersion } from '../../auth/usePerm'

export interface RoleSummary {
  id: number
  code: string
  name: string
  type: string
  defaultScope: string
  status: 'ACTIVE' | 'DISABLED' | 'DELETED'
  builtin: boolean
  version: number
  remark: string | null
  directPermissionCount: number
  effectivePermissionCount: number
  inheritedRoleCount: number
  grantCount: number
}

interface RoleDetail extends RoleSummary {
  directPermissionIds: number[]
  effectivePermissionIds: number[]
  inheritedRoleIds: number[]
}

interface Permission {
  id: number
  code: string
  name: string
  type: string
  module: string
  enabled: boolean
}

interface RoleFormValue { code?: string; name: string; defaultScope: string; remark?: string }

const ADMIN_ROLES_KEY = 'iam-role-admin'
const SCOPES = ['ALL', 'ORG_AND_SUB', 'ORG', 'SELF', 'CUSTOM', 'NONE']

export function canDeleteRole(role: Pick<RoleSummary, 'builtin' | 'grantCount' | 'status'>) {
  return !role.builtin && role.grantCount === 0 && role.status !== 'DELETED'
}

export function groupPermissions(permissions: Permission[]) {
  return permissions.reduce<Record<string, Permission[]>>((groups, permission) => {
    const key = permission.module || 'other'
    ;(groups[key] ??= []).push(permission)
    return groups
  }, {})
}

export default function RoleManagementPanel() {
  const { message } = App.useApp()
  const permVersion = usePermVersion()
  const qc = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [createOpen, setCreateOpen] = useState(false)
  const [copyRole, setCopyRole] = useState<RoleSummary | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [createForm] = Form.useForm<RoleFormValue>()
  const [copyForm] = Form.useForm<{ code: string; name: string }>()

  const roles = useQuery({
    queryKey: [ADMIN_ROLES_KEY, status, keyword, permVersion],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (status) params.set('status', status)
      if (keyword) params.set('keyword', keyword)
      const suffix = params.size ? `?${params.toString()}` : ''
      return (await apiClient.get(`/api/v1/iam/role-admin${suffix}`)).data.data as RoleSummary[]
    },
  })

  const refresh = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: [ADMIN_ROLES_KEY] }),
      qc.invalidateQueries({ queryKey: ['iam-roles'] }),
    ])
  }

  const create = useMutation({
    mutationFn: async (value: RoleFormValue) => apiClient.post('/api/v1/iam/role-admin', value),
    onSuccess: async () => {
      message.success('角色已创建')
      setCreateOpen(false)
      createForm.resetFields()
      await refresh()
    },
    onError: (error) => message.error(errText(error)),
  })

  const copy = useMutation({
    mutationFn: async (value: { code: string; name: string }) =>
      apiClient.post(`/api/v1/iam/role-admin/${copyRole!.id}/copy`, value),
    onSuccess: async () => {
      message.success('角色及其权限配置已复制')
      setCopyRole(null)
      copyForm.resetFields()
      await refresh()
    },
    onError: (error) => message.error(errText(error)),
  })

  const setEnabled = useMutation({
    mutationFn: async (role: RoleSummary) => apiClient.post(
      `/api/v1/iam/role-admin/${role.id}/enabled`,
      { enabled: role.status !== 'ACTIVE', version: role.version },
    ),
    onSuccess: async () => {
      message.success('角色状态已更新')
      await refresh()
    },
    onError: (error) => message.error(errText(error)),
  })

  const remove = useMutation({
    mutationFn: async (role: RoleSummary) =>
      apiClient.delete(`/api/v1/iam/role-admin/${role.id}?version=${role.version}`),
    onSuccess: async () => {
      message.success('角色已删除')
      await refresh()
    },
    onError: (error) => message.error(errText(error)),
  })

  const columns: ColumnsType<RoleSummary> = [
    {
      title: '角色', key: 'role', width: 230,
      render: (_, role) => (
        <Space direction="vertical" size={0}>
          <Space size={6}>
            <Typography.Text strong>{role.name}</Typography.Text>
            {role.builtin && <Tag color="blue">内置</Tag>}
          </Space>
          <Typography.Text type="secondary" copyable>{role.code}</Typography.Text>
        </Space>
      ),
    },
    { title: '默认范围', dataIndex: 'defaultScope', width: 130, render: (value) => <Tag>{value}</Tag> },
    {
      title: '权限', key: 'permissions', width: 150,
      render: (_, role) => `${role.directPermissionCount} 直接 / ${role.effectivePermissionCount} 有效`,
    },
    { title: '继承角色', dataIndex: 'inheritedRoleCount', width: 100 },
    { title: '授权记录', dataIndex: 'grantCount', width: 100 },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (value: RoleSummary['status']) => (
        <Tag color={value === 'ACTIVE' ? 'success' : value === 'DISABLED' ? 'warning' : 'default'}>{value}</Tag>
      ),
    },
    {
      title: '操作', key: 'operation', width: 310, fixed: 'right',
      render: (_, role) => {
        const deleteReason = role.builtin ? '内置角色不能删除'
          : role.grantCount > 0 ? '角色已有授权记录，不能删除'
            : role.status === 'DELETED' ? '角色已删除' : ''
        return (
          <Space size={2} wrap>
            <Button type="link" size="small" onClick={() => setSelectedId(role.id)}>配置</Button>
            <Button type="link" size="small" onClick={() => {
              setCopyRole(role)
              copyForm.setFieldsValue({ code: `${role.code}_COPY`, name: `${role.name}（副本）` })
            }}>复制</Button>
            <Popconfirm
              title={`${role.status === 'ACTIVE' ? '停用' : '启用'}角色？`}
              description={role.status === 'ACTIVE' ? '停用后已有授权会立即失效，且不能再新增授权。' : '启用后该角色已有授权会重新生效。'}
              onConfirm={() => setEnabled.mutate(role)}
              disabled={role.builtin || role.status === 'DELETED'}
            >
              <Tooltip title={role.builtin ? '内置角色不能停用' : undefined}>
                <Button type="link" size="small" disabled={role.builtin || role.status === 'DELETED'}>
                  {role.status === 'ACTIVE' ? '停用' : '启用'}
                </Button>
              </Tooltip>
            </Popconfirm>
            <Popconfirm title="删除这个角色？" description="删除后不可恢复，角色编码也不会重新开放。"
              onConfirm={() => remove.mutate(role)} disabled={!canDeleteRole(role)}>
              <Tooltip title={deleteReason || undefined}>
                <Button type="link" size="small" danger disabled={!canDeleteRole(role)}>删除</Button>
              </Tooltip>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card size="small">
        <Row gutter={[12, 12]} align="middle">
          <Col xs={24} md={10} lg={8}>
            <Input.Search allowClear placeholder="搜索角色名称或编码" onSearch={(value) => setKeyword(value.trim())} />
          </Col>
          <Col xs={16} md={7} lg={5}>
            <Select allowClear placeholder="全部状态" style={{ width: '100%' }} value={status}
              onChange={setStatus} options={[
                { value: 'ACTIVE', label: '启用' },
                { value: 'DISABLED', label: '停用' },
                { value: 'DELETED', label: '已删除' },
              ]} />
          </Col>
          <Col xs={8} md={7} lg={11} style={{ textAlign: 'right' }}>
            <Button type="primary" onClick={() => setCreateOpen(true)}>新增角色</Button>
          </Col>
        </Row>
      </Card>

      <DataCard title="角色列表" query={roles} data={roles.data} emptyText="暂无角色">
        {(rows) => <Table rowKey="id" columns={columns} dataSource={rows} scroll={{ x: 1100 }} />}
      </DataCard>

      <RoleEditor roleId={selectedId} onClose={() => setSelectedId(null)} onChanged={refresh} />

      <Modal title="新增角色" open={createOpen} confirmLoading={create.isPending}
        onCancel={() => setCreateOpen(false)} onOk={() => createForm.submit()} destroyOnClose>
        <RoleBaseForm form={createForm} includeCode onFinish={(value) => create.mutate(value)} />
      </Modal>

      <Modal title={`复制角色：${copyRole?.name ?? ''}`} open={Boolean(copyRole)} confirmLoading={copy.isPending}
        onCancel={() => setCopyRole(null)} onOk={() => copyForm.submit()} destroyOnClose>
        <Form form={copyForm} layout="vertical" onFinish={(value) => copy.mutate(value)} preserve={false}>
          <Form.Item name="code" label="新角色编码" rules={roleCodeRules}><Input maxLength={64} /></Form.Item>
          <Form.Item name="name" label="新角色名称" rules={[{ required: true }]}><Input maxLength={128} /></Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

function RoleEditor({ roleId, onClose, onChanged }: {
  roleId: number | null
  onClose: () => void
  onChanged: () => Promise<void>
}) {
  const { message } = App.useApp()
  const permVersion = usePermVersion()
  const qc = useQueryClient()
  const [basicForm] = Form.useForm<RoleFormValue>()
  const [permissionIds, setPermissionIds] = useState<number[]>([])
  const [inheritedRoleIds, setInheritedRoleIds] = useState<number[]>([])

  const detail = useQuery({
    queryKey: [ADMIN_ROLES_KEY, 'detail', roleId, permVersion],
    queryFn: async () => (await apiClient.get(`/api/v1/iam/role-admin/${roleId}`)).data.data as RoleDetail,
    enabled: roleId != null,
  })
  const permissions = useQuery({
    queryKey: ['iam-permission-catalog', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/iam/permissions/catalog')).data.data as Permission[],
    enabled: roleId != null,
    staleTime: 5 * 60_000,
  })
  const roleOptions = useQuery({
    queryKey: [ADMIN_ROLES_KEY, 'options', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/iam/role-admin')).data.data as RoleSummary[],
    enabled: roleId != null,
  })

  useEffect(() => {
    if (!detail.data) return
    basicForm.setFieldsValue({
      name: detail.data.name,
      defaultScope: detail.data.defaultScope,
      remark: detail.data.remark ?? undefined,
    })
    setPermissionIds(detail.data.directPermissionIds)
    setInheritedRoleIds(detail.data.inheritedRoleIds)
  }, [basicForm, detail.data])

  const afterSave = async (text: string) => {
    message.success(text)
    await qc.invalidateQueries({ queryKey: [ADMIN_ROLES_KEY, 'detail', roleId] })
    await onChanged()
  }

  const update = useMutation({
    mutationFn: async (value: RoleFormValue) => apiClient.put(`/api/v1/iam/role-admin/${roleId}`, {
      name: value.name, defaultScope: value.defaultScope, remark: value.remark,
      version: detail.data!.version,
    }),
    onSuccess: () => afterSave('角色基本信息已保存'),
    onError: (error) => message.error(errText(error)),
  })
  const savePermissions = useMutation({
    mutationFn: async () => apiClient.put(`/api/v1/iam/role-admin/${roleId}/permissions`, {
      permissionIds, version: detail.data!.version,
    }),
    onSuccess: () => afterSave('角色权限已保存并立即生效'),
    onError: (error) => message.error(errText(error)),
  })
  const saveInheritance = useMutation({
    mutationFn: async () => apiClient.put(`/api/v1/iam/role-admin/${roleId}/inheritance`, {
      inheritedRoleIds, version: detail.data!.version,
    }),
    onSuccess: () => afterSave('角色继承关系已保存并立即生效'),
    onError: (error) => message.error(errText(error)),
  })

  const permissionGroups = useMemo(() => groupPermissions((permissions.data ?? []).filter((item) => item.enabled)), [permissions.data])
  const matrixProtected = detail.data?.builtin && detail.data.code === 'SUPER_ADMIN'

  return (
    <Drawer title={detail.data ? `配置角色：${detail.data.name}` : '配置角色'} width={720}
      open={roleId != null} onClose={onClose} loading={detail.isLoading} destroyOnClose>
      {detail.isError && <Typography.Text type="danger">{errText(detail.error)}</Typography.Text>}
      {detail.data && (
        <Tabs items={[
          {
            key: 'basic', label: '基本信息', children: (
              <Form form={basicForm} layout="vertical" onFinish={(value) => update.mutate(value)}>
                <Form.Item label="角色编码"><Input value={detail.data.code} disabled /></Form.Item>
                <RoleBaseFields />
                <Button type="primary" htmlType="submit" loading={update.isPending}>保存基本信息</Button>
              </Form>
            ),
          },
          {
            key: 'permissions', label: `权限配置（${permissionIds.length}）`, children: (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                {matrixProtected && <Typography.Text type="warning">超级管理员矩阵受保护，避免误操作锁死管理入口。</Typography.Text>}
                <Collapse items={Object.entries(permissionGroups).map(([module, items]) => ({
                  key: module,
                  label: `${module}（${items.filter((item) => permissionIds.includes(item.id)).length}/${items.length}）`,
                  children: (
                    <Checkbox.Group value={permissionIds} disabled={matrixProtected}
                      onChange={(values) => setPermissionIds(values.map(Number))}>
                      <Row gutter={[8, 10]}>
                        {items.map((permission) => (
                          <Col xs={24} md={12} key={permission.id}>
                            <Checkbox value={permission.id}>
                              {permission.name} <Typography.Text type="secondary">{permission.code}</Typography.Text>
                            </Checkbox>
                          </Col>
                        ))}
                      </Row>
                    </Checkbox.Group>
                  ),
                }))} />
                <Typography.Text type="secondary">
                  当前有效权限 {detail.data.effectivePermissionIds.length} 项；继承得到的权限不会作为直接权限勾选。
                </Typography.Text>
                <Button type="primary" disabled={matrixProtected} loading={savePermissions.isPending}
                  onClick={() => savePermissions.mutate()}>保存权限配置</Button>
              </Space>
            ),
          },
          {
            key: 'inheritance', label: `角色继承（${inheritedRoleIds.length}）`, children: (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Typography.Paragraph type="secondary">
                  当前角色会获得所选角色的全部有效权限。系统会拒绝自继承、跨租户和直接或间接环路。
                </Typography.Paragraph>
                <Select mode="multiple" allowClear showSearch optionFilterProp="label" style={{ width: '100%' }}
                  value={inheritedRoleIds} disabled={matrixProtected}
                  onChange={setInheritedRoleIds}
                  loading={roleOptions.isLoading}
                  options={(roleOptions.data ?? []).filter((role) => role.id !== roleId).map((role) => ({
                    value: role.id, label: `${role.name}（${role.code} · ${role.status}）`,
                  }))} />
                <Button type="primary" disabled={matrixProtected} loading={saveInheritance.isPending}
                  onClick={() => saveInheritance.mutate()}>保存继承关系</Button>
              </Space>
            ),
          },
        ]} />
      )}
    </Drawer>
  )
}

const roleCodeRules = [
  { required: true, message: '请输入角色编码' },
  { pattern: /^[A-Za-z][A-Za-z0-9_-]{1,63}$/, message: '2~64 位，以字母开头，只能包含字母、数字、_、-' },
]

function RoleBaseFields() {
  return (
    <>
      <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
        <Input maxLength={128} />
      </Form.Item>
      <Form.Item name="defaultScope" label="默认数据范围" rules={[{ required: true }]}>
        <Select options={SCOPES.map((value) => ({ value, label: value }))} />
      </Form.Item>
      <Form.Item name="remark" label="说明"><Input.TextArea maxLength={500} showCount rows={3} /></Form.Item>
    </>
  )
}

function RoleBaseForm({ form, includeCode, onFinish }: {
  form: ReturnType<typeof Form.useForm<RoleFormValue>>[0]
  includeCode?: boolean
  onFinish: (value: RoleFormValue) => void
}) {
  return (
    <Form form={form} layout="vertical" onFinish={onFinish} preserve={false}
      initialValues={{ defaultScope: 'SELF' }}>
      {includeCode && <Form.Item name="code" label="角色编码" rules={roleCodeRules}><Input maxLength={64} /></Form.Item>}
      <RoleBaseFields />
    </Form>
  )
}
