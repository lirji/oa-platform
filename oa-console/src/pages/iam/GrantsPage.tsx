import { useState } from 'react'
import { App, Button, Card, Col, Form, Input, Popconfirm, Row, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataCard } from '../../components/common/DataCard'
import Can from '../../auth/Can'
import { usePermVersion } from '../../auth/usePerm'

interface Role { id: number; code: string; name: string; defaultScope: string }
interface GrantRecord {
  id: number; subjectType: string; subjectId: string; roleId: number
  scopeType: string; grantType: string; validTo: string | null
  reason: string | null; grantedBy: string; grantedAt: string
}

const GRANTS_KEY = 'iam-grants'

export default function GrantsPage() {
  const permVersion = usePermVersion()
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [form] = Form.useForm()
  // ★ 后端 GET /iam/grants 要求 subjectType + subjectId 【都必填】，
  //   所以这一页只能是"先选主体，再看他的授权"，做不了"列出全部授权"。
  const [subject, setSubject] = useState<{ type: string; id: string } | null>(null)

  const roles = useQuery({
    queryKey: ['iam-roles'],
    queryFn: async () => (await apiClient.get('/api/v1/iam/roles')).data.data as Role[],
    staleTime: 5 * 60_000,
  })

  const grants = useQuery({
    queryKey: [GRANTS_KEY, subject, permVersion],
    queryFn: async () =>
      (await apiClient.get(
        `/api/v1/iam/grants?subjectType=${subject!.type}&subjectId=${encodeURIComponent(subject!.id)}`,
      )).data.data as GrantRecord[],
    enabled: Boolean(subject),
  })

  const grant = useMutation({
    mutationFn: async (v: Record<string, unknown>) => apiClient.post('/api/v1/iam/grants', v),
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
      <PageHeader title="授权管理" description="先选一个主体（人或部门），再查看与调整它的授权" />
      <Row gutter={16}>
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
                ]} />
              </Form.Item>
              <Form.Item name="subjectId" label="主体 id" rules={[{ required: true, message: '必填' }]}>
                <Input placeholder="用户 id 或组织 id" />
              </Form.Item>
              <Form.Item name="roleId" label="授予角色（留空则只查询）">
                <Select allowClear loading={roles.isLoading}
                  options={(roles.data ?? []).map((r) => ({
                    value: r.id, label: `${r.name}（${r.code} · 默认 ${r.defaultScope}）`,
                  }))} />
              </Form.Item>
              <Form.Item name="scopeType" label="数据范围" initialValue="SELF">
                <Select options={['ALL', 'ORG_AND_SUB', 'ORG', 'SELF', 'NONE'].map((v) => ({ value: v, label: v }))} />
              </Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" loading={grant.isPending}>查询 / 授权</Button>
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
      </Row>
    </>
  )
}
