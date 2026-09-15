import { useMemo, useState, type Key } from 'react'
import {
  App, Button, Card, Col, Descriptions, Form, Input, InputNumber, Modal, Row, Select, Space, Switch,
  Table, Tag, Tree, Typography,
} from 'antd'
import type { DataNode } from 'antd/es/tree'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText, normalizeError } from '@oa/shared/api/errors'
import { PERM } from '@oa/shared/perm/codes'
import { PageSkeleton, ErrorState, EmptyState } from '../../components/common/AsyncState'
import { usePerm, usePermVersion } from '../../auth/usePerm'
import Can from '../../auth/Can'
import {
  attachChildren, canDrop as canDropPure, collectIds, filterTree, inOrgSubtree,
  indexTree, suggestedChildType,
} from './orgTree'

interface OrgNode {
  id: number; parentId: number | null; code: string; name: string; type: string
  path: string; depth: number; sortOrder: number; status: string
  leaderUserId: string | null
  /** ★ null = 未统计（树接口默认不带）。不要渲染成 0。 */
  memberCount: number | null
  children: OrgNode[]
}

interface DirectoryEntry {
  employeeId: number; userId: string; empNo: string; name: string
  orgId: number | null; orgPath: string | null; orgName: string | null
  positionName: string | null; leader: boolean
}

interface EmployeeBrief { name: string; empNo: string; userId: string }

const TREE_KEY = 'org-tree'
const ORG_TYPES = ['GROUP', 'COMPANY', 'BU', 'CENTER', 'DEPT', 'TEAM', 'SQUAD', 'VIRTUAL'] as const
const TYPE_LABEL: Record<string, string> = {
  GROUP: '集团', COMPANY: '公司', BU: '事业部', CENTER: '中心',
  DEPT: '部门', TEAM: '团队', SQUAD: '组', VIRTUAL: '虚拟组',
}

export default function OrgTreePage() {
  const { message, modal } = App.useApp()
  const qc = useQueryClient()
  const perm = usePerm()
  const permVersion = usePermVersion()
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [keyword, setKeyword] = useState('')
  const [loadedKeys, setLoadedKeys] = useState<number[]>([])
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([])
  const [directOnly, setDirectOnly] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [createForm] = Form.useForm()
  const [editForm] = Form.useForm()

  // ★ 必须传 maxDepth：缺省是 Integer.MAX_VALUE，一次返回整棵树。
  //   3000 节点的嵌套 JSON 一把拉回来 + antd Tree 全量渲染会卡 1–3 秒。
  const tree = useQuery({
    queryKey: [TREE_KEY, 2, permVersion],
    queryFn: async () =>
      (await apiClient.get('/api/v1/org/units/tree', { params: { maxDepth: 2 } })).data.data as OrgNode[],
  })

  const resetLoaded = () => setLoadedKeys([])

  const move = useMutation({
    mutationFn: async (v: { orgId: number; newParentId: number }) =>
      apiClient.put(`/api/v1/org/units/${v.orgId}/parent`, { newParentId: v.newParentId }),
    onSuccess: () => {
      message.success('已移动')
      resetLoaded()
      // ★ 只失效树，不要无参 invalidateQueries() —— 那会把万行通讯录一起重拉
      void qc.invalidateQueries({ queryKey: [TREE_KEY] })
    },
    onError: (e) => {
      const n = normalizeError(e)
      modal.error({ title: '移动失败', content: n.text })
      resetLoaded()
      void qc.invalidateQueries({ queryKey: [TREE_KEY] })
    },
  })

  const create = useMutation({
    mutationFn: async (v: { parentId: number; code: string; name: string; type: string; sortOrder?: number }) =>
      apiClient.post('/api/v1/org/units', v),
    onSuccess: () => {
      message.success('已新建')
      setCreateOpen(false)
      createForm.resetFields()
      resetLoaded()
      void qc.invalidateQueries({ queryKey: [TREE_KEY] })
    },
    onError: (e) => message.error(normalizeError(e).text),
  })

  const update = useMutation({
    mutationFn: async (v: { id: number; name?: string; shortName?: string; type?: string; sortOrder?: number; remark?: string }) =>
      apiClient.put(`/api/v1/org/units/${v.id}`, {
        name: v.name, shortName: v.shortName, type: v.type, sortOrder: v.sortOrder, remark: v.remark,
      }),
    onSuccess: () => {
      message.success('已保存')
      setEditOpen(false)
      void qc.invalidateQueries({ queryKey: [TREE_KEY] })
    },
    onError: (e) => message.error(normalizeError(e).text),
  })

  const nodeIndex = useMemo(() => indexTree(tree.data ?? []), [tree.data])
  const selected = selectedId == null ? null : nodeIndex.get(selectedId) ?? null
  const visible = useMemo(() => filterTree(tree.data ?? [], keyword), [tree.data, keyword])
  const searching = Boolean(keyword.trim())

  const directory = useQuery({
    queryKey: ['org-tree-members', permVersion],
    queryFn: async () =>
      (await apiClient.get('/api/v1/org/directory/page', { params: { size: 200 } })).data.data.items as DirectoryEntry[],
    enabled: perm.has(PERM.EMPLOYEE_VIEW),
  })

  const leader = useQuery({
    queryKey: ['org-leader', selected?.leaderUserId, permVersion],
    queryFn: async () =>
      (await apiClient.get(`/api/v1/org/employees/by-user/${encodeURIComponent(selected!.leaderUserId!)}`))
        .data.data as EmployeeBrief,
    enabled: Boolean(selected?.leaderUserId) && perm.has(PERM.EMPLOYEE_VIEW),
    retry: false,
  })

  const members = useMemo(() => {
    if (!selected || !directory.data) return []
    return directory.data.filter((e) => inOrgSubtree(e, selected, directOnly))
  }, [directory.data, selected, directOnly])

  const canDrop = (dragId: number, dropId: number) => canDropPure(dragId, dropId, nodeIndex)

  const toTreeData = (ns: OrgNode[]): DataNode[] =>
    ns.map((n) => ({
      key: n.id,
      isLeaf: loadedKeys.includes(n.id) && !(n.children?.length),
      title: (
        <span>
          {n.name}
          <Typography.Text type="secondary" style={{ marginInlineStart: 8, fontSize: 12 }}>
            {n.code}
          </Typography.Text>
          <Tag style={{ marginInlineStart: 8 }}>{TYPE_LABEL[n.type] ?? n.type}</Tag>
        </span>
      ),
      children: n.children?.length ? toTreeData(n.children) : undefined,
    }))

  const loadData = async (treeNode: { key: Key }) => {
    const id = Number(treeNode.key)
    const roots = (await apiClient.get('/api/v1/org/units/tree', {
      params: { rootId: id, maxDepth: 1 },
    })).data.data as OrgNode[]
    const kids = roots[0]?.children ?? []
    qc.setQueryData<OrgNode[]>([TREE_KEY, 2, permVersion], (old) =>
      old ? attachChildren(old, id, kids) : old)
    setLoadedKeys((prev) => prev.includes(id) ? prev : [...prev, id])
  }

  if (tree.isLoading) return <PageSkeleton rows={10} />
  if (tree.isError) return <ErrorState message={errText(tree.error)} onRetry={tree.refetch} />

  const draggable = perm.has(PERM.ORG_MOVE) && !searching
  const memberCols: ColumnsType<DirectoryEntry> = [
    { title: '姓名', dataIndex: 'name', width: 100 },
    { title: '工号', dataIndex: 'empNo', width: 110, render: (v) => <span className="mono">{v}</span> },
    { title: '岗位', dataIndex: 'positionName', ellipsis: true, render: (v) => v ?? '—' },
    {
      title: '', dataIndex: 'leader', width: 72,
      render: (v: boolean) => (v ? <Tag color="blue">负责人</Tag> : null),
    },
  ]

  return (
    <>
      <Row gutter={16}>
        <Col xs={24} lg={10}>
          <Card
            title="组织树"
            size="small"
            extra={
              <Can code={PERM.ORG_CREATE}>
                <Button
                  type="primary" size="small" disabled={!selected}
                  onClick={() => {
                    createForm.setFieldsValue({
                      type: selected ? suggestedChildType(selected.type) : 'DEPT',
                      sortOrder: 0,
                    })
                    setCreateOpen(true)
                  }}
                >
                  新建下级
                </Button>
              </Can>
            }
            styles={{ body: { maxHeight: '68vh', overflow: 'auto' } }}
          >
            <Input.Search
              allowClear
              placeholder="搜索已加载的名称 / 编码"
              style={{ marginBottom: 12 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            {visible.length === 0 ? (
              <EmptyState description={searching ? '没有匹配的组织' : undefined} />
            ) : (
              <Tree
                treeData={toTreeData(visible)}
                loadData={searching ? undefined : loadData}
                loadedKeys={loadedKeys}
                draggable={draggable}
                blockNode
                expandedKeys={searching ? collectIds(visible) : expandedKeys}
                onExpand={(keys) => { if (!searching) setExpandedKeys(keys) }}
                selectedKeys={selectedId == null ? [] : [selectedId]}
                onSelect={(keys) => setSelectedId(keys.length ? Number(keys[0]) : null)}
                allowDrop={({ dragNode, dropNode }) => canDrop(Number(dragNode.key), Number(dropNode.key))}
                onDrop={(info) => {
                  const dragId = Number(info.dragNode.key)
                  const dropId = Number(info.node.key)
                  if (!canDrop(dragId, dropId)) { message.warning('不能移动到自己的下级组织'); return }
                  move.mutate({ orgId: dragId, newParentId: dropId })
                }}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card
            title="详情"
            size="small"
            extra={selected && (
              <Can code={PERM.ORG_UPDATE}>
                <Button size="small" onClick={() => {
                  editForm.setFieldsValue({
                    name: selected.name, type: selected.type, sortOrder: selected.sortOrder,
                  })
                  setEditOpen(true)
                }}>
                  编辑
                </Button>
              </Can>
            )}
          >
            {selected ? (
              <Descriptions column={{ xs: 1, md: 2 }} size="small" bordered>
                <Descriptions.Item label="名称">{selected.name}</Descriptions.Item>
                <Descriptions.Item label="编码"><span className="mono">{selected.code}</span></Descriptions.Item>
                <Descriptions.Item label="类型">
                  <Tag>{TYPE_LABEL[selected.type] ?? selected.type}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="层级">{selected.depth}</Descriptions.Item>
                <Descriptions.Item label="路径" span={2}>
                  <span className="mono">{selected.path}</span>
                </Descriptions.Item>
                <Descriptions.Item label="负责人" span={2}>
                  {selected.leaderUserId == null ? '—' : leader.isSuccess
                    ? `${leader.data.name}（${leader.data.empNo}）`
                    : leader.isError
                      ? <span className="mono">{selected.leaderUserId}</span>
                      : '…'}
                </Descriptions.Item>
                <Descriptions.Item label="成员数" span={2}>
                  {perm.has(PERM.EMPLOYEE_VIEW)
                    ? `${members.length} 人（通讯录可见范围，最多 200）`
                    : selected.memberCount == null
                      ? <Typography.Text type="secondary">未统计</Typography.Text>
                      : selected.memberCount}
                </Descriptions.Item>
              </Descriptions>
            ) : (
              <Typography.Text type="secondary">在左侧选择一个组织</Typography.Text>
            )}
          </Card>
          {selected && perm.has(PERM.EMPLOYEE_VIEW) && (
            <Card
              title="部门成员"
              size="small"
              style={{ marginTop: 16 }}
              extra={
                <Space>
                  <Typography.Text type="secondary">仅本级</Typography.Text>
                  <Switch size="small" checked={directOnly} onChange={setDirectOnly} />
                </Space>
              }
            >
              <Table
                rowKey="employeeId"
                columns={memberCols}
                dataSource={members}
                size="small"
                pagination={members.length > 8 ? { pageSize: 8, size: 'small' } : false}
                locale={{ emptyText: directOnly ? '本级没有人' : '这个组织下通讯录里没有人' }}
              />
            </Card>
          )}
        </Col>
      </Row>

      <Modal
        title={selected ? `在「${selected.name}」下新建` : '新建组织'}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={create.isPending}
        destroyOnClose
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={(v: { name: string; code: string; type: string; sortOrder?: number }) => {
            if (!selected) return
            create.mutate({ parentId: selected.id, ...v })
          }}
        >
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '填写名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="code" label="编码"
            extra="字母、数字、点、下划线或短横，最多 64 位，租户内唯一"
            rules={[{ required: true, pattern: /^[A-Za-z0-9_.-]{1,64}$/, message: '编码格式不对' }]}
          >
            <Input className="mono" />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={ORG_TYPES.map((t) => ({ value: t, label: TYPE_LABEL[t] }))} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑组织"
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={() => editForm.submit()}
        confirmLoading={update.isPending}
        destroyOnClose
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={(v: { name: string; type: string; sortOrder?: number; remark?: string }) => {
            if (!selected) return
            update.mutate({ id: selected.id, ...v, sortOrder: v.sortOrder == null ? undefined : Number(v.sortOrder) })
          }}
        >
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '填写名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="类型">
            <Select options={ORG_TYPES.map((t) => ({ value: t, label: TYPE_LABEL[t] }))} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
