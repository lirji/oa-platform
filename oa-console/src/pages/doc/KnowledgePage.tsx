import { useMemo, useState } from 'react'
import {
  Button, Descriptions, Drawer, Input, Space, Table, Tag, Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { EyeOutlined, QuestionCircleOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import type { components } from '@oa/shared/types/openapi'
import { PageHeader } from '../../components/layout/PageHeader'
import { DataCard } from '../../components/common/DataCard'
import { ErrorState, PageSkeleton } from '../../components/common/AsyncState'
import { usePerm, usePermVersion } from '../../auth/usePerm'

type KbDoc = components['schemas']['KbDocView']
type KbExplain = components['schemas']['KbAccessExplain']

/** 知识库列表 + 对象级判权解释器。接口权限与单篇对象权限是两道独立闸门。 */
export default function KnowledgePage() {
  const perm = usePerm()
  const permVersion = usePermVersion()
  const [keyword, setKeyword] = useState('')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [explainId, setExplainId] = useState<number | null>(null)
  const [explainUser, setExplainUser] = useState(perm.userId ?? '')

  const list = useQuery({
    queryKey: ['kb-list', 200, permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/doc/kb?limit=200')).data.data as KbDoc[],
  })
  const detail = useQuery({
    queryKey: ['kb-detail', selectedId, permVersion],
    queryFn: async () => (await apiClient.get(`/api/v1/doc/kb/${selectedId}`)).data.data as KbDoc,
    enabled: selectedId != null,
    retry: false,
  })
  const explain = useQuery({
    queryKey: ['kb-explain', explainId, explainUser, permVersion],
    queryFn: async () => (await apiClient.get(
      `/api/v1/doc/kb/${explainId}/explain?userId=${encodeURIComponent(explainUser.trim())}`,
    )).data.data as KbExplain,
    enabled: explainId != null && Boolean(explainUser.trim()) && perm.has('oa:kb:share'),
    retry: false,
  })

  const rows = useMemo(() => {
    const q = keyword.trim().toLocaleLowerCase('zh-CN')
    if (!q) return list.data ?? []
    return (list.data ?? []).filter((doc) =>
      [doc.title, doc.summary, doc.ownerId].some((value) => value?.toLocaleLowerCase('zh-CN').includes(q)))
  }, [keyword, list.data])

  const columns: ColumnsType<KbDoc> = [
    { title: '标题', dataIndex: 'title', ellipsis: true, render: (value) => <Typography.Text strong>{value ?? '未命名'}</Typography.Text> },
    { title: '摘要', dataIndex: 'summary', ellipsis: true, render: (value) => value || '—' },
    {
      title: '所有者', dataIndex: 'ownerId', width: 140, ellipsis: true,
      render: (value) => (
        <Typography.Text ellipsis={{ tooltip: value }} style={{ width: 120, wordBreak: 'keep-all' }}>
          {value ?? '—'}
        </Typography.Text>
      ),
    },
    { title: '版本', dataIndex: 'version', width: 72, render: (value) => `v${value ?? 0}` },
    {
      title: '更新时间', dataIndex: 'updatedAt', width: 168,
      render: (value) => value ? new Date(value).toLocaleString('zh-CN') : '—',
    },
    {
      title: '操作', key: 'actions', width: perm.has('oa:kb:share') ? 168 : 88, fixed: 'right',
      render: (_, doc) => (
        <Space size={0}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => setSelectedId(doc.id ?? null)}>查看</Button>
          {perm.has('oa:kb:share') && (
            <Button type="link" size="small" icon={<QuestionCircleOutlined />} onClick={() => {
              setExplainUser(perm.userId ?? '')
              setExplainId(doc.id ?? null)
            }}>解释</Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="知识库"
        description="列表只返回你能访问的文档；打开单篇时仍会再次执行对象级判权"
        extra={<Input.Search
          allowClear data-testid="primary-action" placeholder="搜索标题 / 摘要 / 所有者"
          value={keyword} onChange={(event) => setKeyword(event.target.value)} style={{ width: 320 }}
        />}
      />
      <DataCard query={list} data={rows} emptyText={keyword ? '没有匹配的可见文档' : '暂无可见文档'}>
        {(data) => (
          <Table
            rowKey={(doc) => String(doc.id)}
            columns={columns}
            dataSource={data}
            pagination={{ pageSize: 20 }}
            scroll={{ x: 960 }}
          />
        )}
      </DataCard>

      <Drawer title={detail.data?.title ?? '文档详情'} width={640} open={selectedId != null}
        onClose={() => setSelectedId(null)} destroyOnClose>
        {detail.isLoading ? <PageSkeleton /> : detail.isError ? <ErrorState message="无法读取这份文档；它可能不存在，或对象级权限已被收回" /> : detail.data ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions column={1} bordered size="small" items={[
              { key: 'owner', label: '所有者', children: detail.data.ownerId ?? '—' },
              { key: 'version', label: '版本', children: `v${detail.data.version ?? 0}` },
              { key: 'updated', label: '更新时间', children: detail.data.updatedAt ? new Date(detail.data.updatedAt).toLocaleString('zh-CN') : '—' },
            ]} />
            {detail.data.summary && <Typography.Paragraph type="secondary">{detail.data.summary}</Typography.Paragraph>}
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap' }}>{detail.data.body || '（无正文）'}</Typography.Paragraph>
          </Space>
        ) : null}
      </Drawer>

      <Drawer title="对象级访问解释" width={520} open={explainId != null}
        onClose={() => setExplainId(null)} destroyOnClose>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Input value={explainUser} onChange={(event) => setExplainUser(event.target.value)}
            placeholder="Casdoor sub / 用户 id" aria-label="解释对象" />
          {explain.isLoading ? <PageSkeleton rows={4} /> : explain.isError ? <ErrorState message="访问解释加载失败" /> : explain.data ? (
            <Descriptions column={1} bordered size="small" items={[
              { key: 'result', label: '判定', children: <Tag color={explain.data.allowed ? 'success' : 'error'}>{explain.data.allowed ? '允许' : '拒绝'}</Tag> },
              { key: 'level', label: '访问级别', children: explain.data.level ?? '—' },
              { key: 'engine', label: '判定引擎', children: explain.data.decidedBy ?? '—' },
              { key: 'detail', label: '原因', children: explain.data.detail ?? '—' },
            ]} />
          ) : <Typography.Text type="secondary">输入用户 id 后查看判定来源</Typography.Text>}
        </Space>
      </Drawer>
    </>
  )
}
