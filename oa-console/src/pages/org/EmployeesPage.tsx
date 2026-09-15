import { useEffect, useMemo, useState } from 'react'
import { Drawer, Input, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { PERM } from '@oa/shared/perm/codes'
import { DataCard } from '../../components/common/DataCard'
import { usePerm, usePermVersion } from '../../auth/usePerm'

interface Entry {
  employeeId: number; userId: string; empNo: string; name: string; email: string | null
  status: string; orgId: number | null; orgPath: string | null; orgName: string | null
  positionName: string | null; mobile: string | null; leader: boolean; syncSeq: number
}

interface Assignment {
  id?: number; assignmentType?: string; orgName?: string; positionName?: string
  leader?: boolean; validFrom?: string; validTo?: string
}

interface EmployeeBrief {
  name?: string; empNo?: string; status?: string; employmentType?: string
  primaryOrgName?: string; hireDate?: string; email?: string
}

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'success', PROBATION: 'processing', LEAVING: 'warning', LEFT: 'default',
}

export default function EmployeesPage() {
  const perm = usePerm()
  const permVersion = usePermVersion()
  const [keyword, setKeyword] = useState('')
  const [debounced, setDebounced] = useState('')
  const [status, setStatus] = useState<string>()
  const [picked, setPicked] = useState<Entry | null>(null)

  useEffect(() => {
    const t = setTimeout(() => setDebounced(keyword.trim()), 200)
    return () => clearTimeout(t)
  }, [keyword])

  const q = useQuery({
    queryKey: ['employees', debounced, permVersion],
    queryFn: async () =>
      (await apiClient.get('/api/v1/org/directory/page', {
        params: { size: 200, keyword: debounced || undefined },
      })).data.data.items as Entry[],
  })

  const rows = useMemo(() => {
    const items = q.data ?? []
    return status ? items.filter((e) => e.status === status) : items
  }, [q.data, status])

  const detail = useQuery({
    queryKey: ['employee-brief', picked?.userId, permVersion],
    queryFn: async () =>
      (await apiClient.get(`/api/v1/org/employees/by-user/${encodeURIComponent(picked!.userId)}`))
        .data.data as EmployeeBrief,
    enabled: Boolean(picked?.userId) && perm.has(PERM.EMPLOYEE_VIEW),
    retry: false,
  })

  const assignments = useQuery({
    queryKey: ['employee-assignments', picked?.userId, permVersion],
    queryFn: async () =>
      (await apiClient.get(`/api/v1/org/employees/by-user/${encodeURIComponent(picked!.userId)}/assignments`))
        .data.data as Assignment[],
    enabled: Boolean(picked?.userId) && perm.has(PERM.EMPLOYEE_VIEW),
  })

  const chain = useQuery({
    queryKey: ['employee-chain', picked?.userId, permVersion],
    queryFn: async () =>
      (await apiClient.get(`/api/v1/org/employees/by-user/${encodeURIComponent(picked!.userId)}/manager-chain`))
        .data.data as string[],
    enabled: Boolean(picked?.userId) && perm.has(PERM.EMPLOYEE_VIEW),
  })

  const cols: ColumnsType<Entry> = [
    { title: '工号', dataIndex: 'empNo', width: 120, render: (v) => <span className="mono">{v}</span> },
    { title: '姓名', dataIndex: 'name', width: 120 },
    { title: '部门', dataIndex: 'orgName', width: 200, ellipsis: true, render: (v) => v ?? '-' },
    { title: '岗位', dataIndex: 'positionName', width: 160, render: (v) => v ?? '-' },
    {
      // ★ mobile 由后端 @Sensitive 在序列化层脱敏：没有 oa:field:mobile 的人拿到的是掩码。
      //   前端不做任何判断 —— 判断在后端，这里只负责显示拿到的东西。
      title: '手机', dataIndex: 'mobile', width: 140, render: (v) => v ?? '—',
    },
    { title: '负责人', dataIndex: 'leader', width: 90, render: (v: boolean) => (v ? <Tag color="blue">是</Tag> : '-') },
    {
      title: '状态', dataIndex: 'status', width: 110,
      render: (v: string) => <Tag color={STATUS_COLOR[v] ?? 'default'}>{v}</Tag>,
    },
  ]

  return (
    <>
      <DataCard
        query={q}
        data={rows}
        scopeModule="org"
        extra={
          <Space wrap>
            <Input.Search
              allowClear
              placeholder="姓名 / 工号 / 部门"
              style={{ width: 240 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <Select
              allowClear
              placeholder="状态"
              style={{ width: 140 }}
              value={status}
              onChange={setStatus}
              options={[
                { value: 'ACTIVE', label: '在职' },
                { value: 'PROBATION', label: '试用' },
                { value: 'LEAVING', label: '离职中' },
              ]}
            />
          </Space>
        }
        scoped={!status && !debounced}
        emptyText={status || debounced ? '没有匹配的人' : undefined}
      >
        {(data) => (
          <Table
            rowKey="employeeId"
            columns={cols}
            dataSource={data}
            virtual
            scroll={{ x: 980, y: 520 }}
            pagination={false}
            size="middle"
            onRow={(row) => ({
              onClick: () => setPicked(row),
              style: { cursor: 'pointer' },
            })}
          />
        )}
      </DataCard>

      <Drawer
        title={picked ? `${picked.name} · ${picked.empNo}` : '员工'}
        open={Boolean(picked)}
        onClose={() => setPicked(null)}
        width={420}
      >
        {picked && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
              {detail.data?.primaryOrgName ?? picked.orgName ?? '—'}
              {' · '}
              {picked.positionName ?? '—'}
              {detail.data?.employmentType ? ` · ${detail.data.employmentType}` : ''}
            </Typography.Paragraph>
            <div>
              <Typography.Text strong>任职</Typography.Text>
              <Table
                style={{ marginTop: 8 }}
                size="small"
                rowKey={(r, i) => String(r.id ?? i)}
                pagination={false}
                loading={assignments.isLoading}
                dataSource={assignments.data ?? []}
                columns={[
                  { title: '部门', dataIndex: 'orgName', ellipsis: true },
                  { title: '岗位', dataIndex: 'positionName', width: 100, render: (v) => v ?? '—' },
                  { title: '类型', dataIndex: 'assignmentType', width: 90 },
                ]}
              />
            </div>
            <div>
              <Typography.Text strong>汇报链</Typography.Text>
              <Typography.Paragraph type="secondary" style={{ margin: '8px 0 0' }}>
                {chain.isLoading
                  ? '…'
                  : (chain.data ?? []).length
                    ? chain.data!.map((id) => {
                      const person = q.data?.find((e) => e.userId === id)
                      return (
                        <div key={id}>
                          {person ? `${person.name}（${person.empNo}）` : <span className="mono">{id}</span>}
                        </div>
                      )
                    })
                    : '没有实线上司'}
              </Typography.Paragraph>
            </div>
          </Space>
        )}
      </Drawer>
    </>
  )
}
