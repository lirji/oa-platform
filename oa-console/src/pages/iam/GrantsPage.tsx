import { useEffect, useMemo, useState } from 'react'
import { App, Button, Card, Col, DatePicker, Form, Input, Popconfirm, Row, Select, Space, Switch, Table, Tabs, Tag, TreeSelect } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataCard } from '../../components/common/DataCard'
import Can from '../../auth/Can'
import { usePerm, usePermVersion } from '../../auth/usePerm'
import { PERM } from '@oa/shared/perm/codes'
import { AbacPanel, UserGroupsPanel, type UserGroup } from './IamPolicyPanels'
import RoleManagementPanel from './RoleManagementPanel'
import IdentityCatalogPanel from './IdentityCatalogPanel'
import AccessRequestPanel from './AccessRequestPanel'
import PermissionDelegationPanel from './PermissionDelegationPanel'
import RiskFindingsPanel from './RiskFindingsPanel'

interface Role { id: number; code: string; name: string; defaultScope: string }
interface GrantRecord {
  id: number; subjectType: string; subjectId: string; roleId: number
  scopeType: string; grantType: string; validTo: string | null
  reason: string | null; grantedBy: string; grantedAt: string
}
interface DirectoryEntry { userId: string; name: string; orgName: string | null }
interface OrgNode { id: number; name: string; code: string; children?: OrgNode[] }
interface ElevationRequest {
  id: number; requesterId: string; roleId: number; roleCode: string; roleName: string
  hours: number; reason: string; status: string; requestedAt: string
  decidedBy?: string | null; decidedAt?: string | null; decisionReason?: string | null; grantId?: number | null
}

const GRANTS_KEY = 'iam-grants'

export default function GrantsPage() {
  const permVersion = usePermVersion()
  const perm = usePerm()
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [form] = Form.useForm()
  const selectedSubjectType = Form.useWatch('subjectType', form)
  const selectedRoleId = Form.useWatch('roleId', form)
  const selectedScope = Form.useWatch('scopeType', form)
  const selectedGrantType = Form.useWatch('grantType', form)
  // ★ 后端 GET /iam/grants 要求 subjectType + subjectId 【都必填】，
  //   所以这一页只能是"先选主体，再看他的授权"，做不了"列出全部授权"。
  const [subject, setSubject] = useState<{ type: string; id: string } | null>(null)

  const roles = useQuery({
    queryKey: ['iam-roles'],
    queryFn: async () => (await apiClient.get('/api/v1/iam/roles')).data.data as Role[],
    staleTime: 5 * 60_000,
  })

  const groups = useQuery({
    queryKey: ['iam-user-groups', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/iam/groups?status=ACTIVE')).data.data as UserGroup[],
    enabled: selectedSubjectType === 'USER_GROUP',
  })

  const directory = useQuery({
    queryKey: ['iam-grant-users', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/org/directory?limit=200')).data.data as DirectoryEntry[],
    enabled: selectedSubjectType === 'USER',
  })

  const orgTree = useQuery({
    queryKey: ['iam-grant-org-tree', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/org/units/tree?maxDepth=20')).data.data as OrgNode[],
    enabled: selectedSubjectType === 'ORG_UNIT' || selectedScope === 'CUSTOM',
  })

  const selectedRole = roles.data?.find((role) => role.id === selectedRoleId)
  useEffect(() => {
    if (selectedRole) form.setFieldValue('scopeType', selectedRole.defaultScope)
  }, [form, selectedRole])

  const scopeOptions = useMemo(() => assignableScopes(selectedRole?.defaultScope ?? 'SELF')
    .map((value) => ({ value, label: value })), [selectedRole?.defaultScope])

  const grants = useQuery({
    queryKey: [GRANTS_KEY, subject, permVersion],
    queryFn: async () =>
      (await apiClient.get(
        `/api/v1/iam/grants?subjectType=${subject!.type}&subjectId=${encodeURIComponent(subject!.id)}`,
      )).data.data as GrantRecord[],
    enabled: Boolean(subject),
  })

  const grant = useMutation({
    mutationFn: async (v: Record<string, any>) => apiClient.post('/api/v1/iam/grants', {
      ...v,
      validTo: v.grantType === 'TEMPORARY' ? v.validTo?.toISOString() : null,
      scopeOrgIds: v.scopeType === 'CUSTOM' ? v.scopeOrgIds : [],
    }),
    onSuccess: () => {
      message.success('已授权')
      qc.invalidateQueries({ queryKey: [GRANTS_KEY] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const revoke = useMutation({
    mutationFn: async (id: number) => apiClient.delete(`/api/v1/iam/grants/${id}?reason=控制台撤销`),
    onSuccess: () => {
      // ★ 刻意【不做乐观更新】：撤权失败若已把行移出列表，用户会以为撤成功了。
      message.success('已撤销（收权走全局 epoch，1 秒内全节点生效）')
      qc.invalidateQueries({ queryKey: [GRANTS_KEY] })
    },
    onError: (e) => message.error(errText(e)),
  })

  const roleName = (id: number) => roles.data?.find((r) => r.id === id)?.name ?? `#${id}`

  const cols: ColumnsType<GrantRecord> = [
    { title: '角色', dataIndex: 'roleId', width: 160, render: (v: number) => roleName(v) },
    { title: '数据范围', dataIndex: 'scopeType', width: 130, render: (v) => <Tag>{v}</Tag> },
    {
      title: '类型', dataIndex: 'grantType', width: 120,
      render: (v: string) => <Tag color={v === 'TEMPORARY' ? 'warning' : 'default'}>{v}</Tag>,
    },
    {
      title: '有效期至', dataIndex: 'validTo', width: 180,
      render: (v: string | null) => (v ? new Date(v).toLocaleString('zh-CN') : '永久'),
    },
    { title: '事由', dataIndex: 'reason', ellipsis: true, render: (v) => v ?? '-' },
    { title: '授权人', dataIndex: 'grantedBy', width: 130 },
    {
      title: '操作', key: 'op', width: 100, fixed: 'right',
      render: (_, r) => (
        <Can code="oa:iam:revoke">
          <Popconfirm title="撤销这条授权？" description="收权立即生效，1 秒内全节点作废。"
            onConfirm={() => revoke.mutate(r.id)}>
            <Button type="link" size="small" danger loading={revoke.isPending}>撤销</Button>
          </Popconfirm>
        </Can>
      ),
    },
  ]

  return (
    <>
      <PageHeader title="身份与授权治理" description="身份目录、角色、主体授权、用户组和 ABAC 条件" />
      <Tabs items={[
        ...(perm.has('oa:iam:identity:view') ? [
          { key: 'identities', label: '身份目录', children: <IdentityCatalogPanel /> },
        ] : []),
        {
          key: 'grants', label: '主体授权', children: <Row gutter={16}>
        <Col xs={24} lg={9}>
          <Card size="small" title="选择主体 / 新增授权">
            <Form form={form} layout="vertical" onFinish={(v) => {
              setSubject({ type: v.subjectType, id: v.subjectId })
              if (v.roleId) grant.mutate(v)
            }}>
              <Form.Item name="subjectType" label="主体类型" initialValue="USER" rules={[{ required: true }]}>
                <Select options={[
                  { value: 'USER', label: '用户' },
                  { value: 'ORG_UNIT', label: '组织（可含下级）' },
                  { value: 'POSITION', label: '岗位' },
                  { value: 'USER_GROUP', label: '用户组' },
                ]} />
              </Form.Item>
              {selectedSubjectType === 'USER_GROUP' ? (
                <Form.Item name="subjectId" label="用户组" rules={[{ required: true, message: '必填' }]}>
                  <Select loading={groups.isLoading} showSearch optionFilterProp="label"
                    placeholder="选择一个已启用用户组"
                    options={(groups.data ?? []).map((g) => ({ value: String(g.id), label: `${g.name}（${g.code}）` }))} />
                </Form.Item>
              ) : selectedSubjectType === 'USER' ? (
                <Form.Item name="subjectId" label="用户" rules={[{ required: true, message: '请选择用户' }]}>
                  <Select loading={directory.isLoading} showSearch optionFilterProp="label"
                    placeholder="按姓名或账号选择用户"
                    options={(directory.data ?? []).map((user) => ({
                      value: user.userId, label: `${user.name}（${user.userId}${user.orgName ? ` · ${user.orgName}` : ''}）`,
                    }))} />
                </Form.Item>
              ) : selectedSubjectType === 'ORG_UNIT' ? (
                <Form.Item name="subjectId" label="组织" rules={[{ required: true, message: '请选择组织' }]}>
                  <TreeSelect loading={orgTree.isLoading} treeData={toOrgTreeData(orgTree.data ?? [])}
                    treeDefaultExpandAll showSearch treeNodeFilterProp="title" placeholder="选择授权主体组织" />
                </Form.Item>
              ) : (
                <Form.Item name="subjectId" label="主体 id" rules={[{ required: true, message: '必填' }]}>
                  <Input placeholder="岗位 id" />
                </Form.Item>
              )}
              <Form.Item name="roleId" label="授予角色（留空则只查询）">
                <Select allowClear loading={roles.isLoading}
                  options={(roles.data ?? []).map((r) => ({
                    value: r.id, label: `${r.name}（${r.code} · 默认 ${r.defaultScope}）`,
                  }))} />
              </Form.Item>
              <Form.Item name="scopeType" label="数据范围" initialValue="SELF" rules={[{ required: true }]} extra={
                selectedRole ? `已自动采用角色默认范围；该范围同时作为角色可分配上限：${selectedRole.defaultScope}` : '先选择角色后自动带出默认范围'
              }>
                <Select options={scopeOptions} />
              </Form.Item>
              {selectedScope === 'CUSTOM' && (
                <Form.Item name="scopeOrgIds" label="自定义组织范围"
                  rules={[{ required: true, type: 'array', min: 1, message: '至少选择一个组织' }]}>
                  <TreeSelect multiple treeCheckable showCheckedStrategy={TreeSelect.SHOW_PARENT}
                    loading={orgTree.isLoading} treeData={toOrgTreeData(orgTree.data ?? [])}
                    treeDefaultExpandAll treeNodeFilterProp="title" placeholder="选择华东大区等组织节点" />
                </Form.Item>
              )}
              {(selectedScope === 'CUSTOM' || selectedSubjectType === 'ORG_UNIT') && (
                <Form.Item name="includeDescendants" label="包含下级组织" valuePropName="checked" initialValue>
                  <Switch checkedChildren="包含" unCheckedChildren="仅本组织" />
                </Form.Item>
              )}
              <Form.Item name="grantType" label="授权类型" initialValue="PERMANENT" rules={[{ required: true }]}>
                <Select options={[
                  { value: 'PERMANENT', label: '永久授权' },
                  { value: 'TEMPORARY', label: '临时授权' },
                ]} />
              </Form.Item>
              {selectedGrantType === 'TEMPORARY' && (
                <Form.Item name="validTo" label="有效期至" rules={[{ required: true, message: '临时授权必须设置到期时间' }]}>
                  <DatePicker showTime style={{ width: '100%' }} />
                </Form.Item>
              )}
              <Form.Item name="reason" label="授权事由" rules={[{ required: true, whitespace: true, message: '请填写授权事由' }]}>
                <Input.TextArea rows={2} maxLength={500} showCount />
              </Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" loading={grant.isPending} data-testid="primary-action">查询 / 授权</Button>
              </Space>
            </Form>
          </Card>
        </Col>
        <Col xs={24} lg={15}>
          <DataCard
            title={subject ? `${subject.type} ${subject.id} 的授权` : '授权列表'}
            query={grants} data={grants.data} emptyText={subject ? '该主体暂无授权' : '先在左侧选择主体'}
          >
            {(rows) => (
              <Table rowKey="id" columns={cols} dataSource={rows}
                pagination={false} scroll={{ x: 900 }} size="middle" />
            )}
          </DataCard>
        </Col>
      </Row>,
        },
        ...(perm.has('oa:iam:admin') ? [
          { key: 'roles', label: '角色管理', children: <RoleManagementPanel /> },
          { key: 'groups', label: '用户组', children: <UserGroupsPanel /> },
          { key: 'abac', label: 'ABAC 条件', children: <AbacPanel /> },
        ] : []),
        ...(perm.has('oa:iam:request') ? [
          { key: 'access-requests', label: '权限申请', children: <AccessRequestPanel /> },
        ] : []),
        ...(perm.has(PERM.IAM_DELEGATE) ? [
          { key: 'permission-delegations', label: '权限委托', children: <PermissionDelegationPanel /> },
        ] : []),
        ...(perm.has(PERM.IAM_ADMIN) ? [
          { key: 'risk-findings', label: '风险发现', children: <RiskFindingsPanel /> },
        ] : []),
        ...(perm.has('oa:iam:elevation:approve') ? [
          { key: 'elevation-approval', label: '提权审批', children: <ElevationApprovalPanel /> },
        ] : []),
      ]} />
    </>
  )
}

function ElevationApprovalPanel() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const permVersion = usePermVersion()
  const requests = useQuery({
    queryKey: ['elevation-requests', 'PENDING', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/iam/elevation-requests?status=PENDING&limit=200'))
      .data.data as ElevationRequest[],
  })
  const decide = useMutation({
    mutationFn: async ({ id, action }: { id: number; action: 'approve' | 'reject' }) =>
      apiClient.post(`/api/v1/iam/elevation-requests/${id}/${action}`, {
        reason: action === 'approve' ? '权限管理员批准' : '权限管理员拒绝',
      }),
    onSuccess: (_, v) => {
      message.success(v.action === 'approve' ? '已批准，临时权限立即生效' : '已拒绝申请')
      qc.invalidateQueries({ queryKey: ['elevation-requests'] })
    },
    onError: (e) => message.error(errText(e)),
  })
  const columns: ColumnsType<ElevationRequest> = [
    { title: '申请人', dataIndex: 'requesterId', width: 140 },
    { title: '提权角色', key: 'role', width: 220, render: (_, r) => `${r.roleName}（${r.roleCode}）` },
    { title: '时长', dataIndex: 'hours', width: 90, render: (v) => `${v} 小时` },
    { title: '申请理由', dataIndex: 'reason', ellipsis: true },
    { title: '申请时间', dataIndex: 'requestedAt', width: 190, render: (v) => new Date(v).toLocaleString('zh-CN') },
    {
      title: '操作', key: 'action', width: 170, fixed: 'right', render: (_, r) => <Space>
        <Popconfirm title="批准这次临时提权？" description="批准后立即生效且全程留痕。"
          onConfirm={() => decide.mutate({ id: r.id, action: 'approve' })}>
          <Button type="link" size="small" loading={decide.isPending}>批准</Button>
        </Popconfirm>
        <Popconfirm title="拒绝这次临时提权？" onConfirm={() => decide.mutate({ id: r.id, action: 'reject' })}>
          <Button type="link" danger size="small" loading={decide.isPending}>拒绝</Button>
        </Popconfirm>
      </Space>,
    },
  ]
  return <DataCard title="待审批的临时提权" query={requests} data={requests.data} emptyText="当前没有待审批申请">
    {(rows) => <Table rowKey="id" columns={columns} dataSource={rows} pagination={false} scroll={{ x: 900 }} />}
  </DataCard>
}

export function assignableScopes(maximum: string) {
  if (maximum === 'ALL') return ['ALL', 'ORG_AND_SUB', 'ORG', 'CUSTOM', 'SELF', 'NONE']
  if (maximum === 'ORG_AND_SUB') return ['ORG_AND_SUB', 'ORG', 'CUSTOM', 'SELF', 'NONE']
  if (maximum === 'CUSTOM') return ['CUSTOM', 'ORG', 'SELF', 'NONE']
  if (maximum === 'ORG') return ['ORG', 'SELF', 'NONE']
  if (maximum === 'NONE') return ['NONE']
  return ['SELF', 'NONE']
}

function toOrgTreeData(nodes: OrgNode[]): any[] {
  return nodes.map((node) => ({
    value: node.id,
    title: `${node.name}（${node.code}）`,
    children: toOrgTreeData(node.children ?? []),
  }))
}
