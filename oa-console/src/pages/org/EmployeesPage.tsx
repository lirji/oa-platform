import { Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataCard } from '../../components/common/DataCard'
import { ScopeBanner } from '../../components/common/ScopeHint'

interface Entry {
  employeeId: number; userId: string; empNo: string; name: string; email: string | null
  status: string; orgName: string | null; positionName: string | null
  mobile: string | null; leader: boolean; syncSeq: number
}

export default function EmployeesPage() {
  const q = useQuery({
    queryKey: ['employees', 200],
    queryFn: async () =>
      (await apiClient.get('/api/v1/org/directory/page?size=200')).data.data.items as Entry[],
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
      title: '状态', dataIndex: 'status', width: 90,
      render: (v: string) => <Tag color={v === 'ACTIVE' ? 'success' : 'default'}>{v}</Tag>,
    },
  ]

  return (
    <>
      <PageHeader title="员工管理" description="数据按你的可见范围过滤" />
      <ScopeBanner module="org" />
      <DataCard query={q} data={q.data} scoped scopeModule="org">
        {(rows) => (
          <Table
            rowKey="employeeId" columns={cols} dataSource={rows}
            // antd 5.9+ 内置虚拟滚动：零新依赖，且与全站 Table 的主题/排序一致。
            // 固定行高足够（这一页是规整表格，不是可变高的卡片）。
            virtual scroll={{ x: 940, y: 520 }}
            pagination={false} size="middle"
          />
        )}
      </DataCard>
    </>
  )
}
