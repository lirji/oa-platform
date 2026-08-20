import { useMemo, useState } from 'react'
import { App, Card, Col, Descriptions, Row, Tag, Tree, Typography } from 'antd'
import type { TreeDataNode } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText, normalizeError } from '@oa/shared/api/errors'
import { PageHeader } from '../../components/layout/PageHeader'
import { PageSkeleton, ErrorState } from '../../components/common/AsyncState'
import { ScopeBanner } from '../../components/common/ScopeHint'
import { usePerm } from '../../auth/usePerm'

interface OrgNode {
  id: number; parentId: number | null; code: string; name: string; type: string
  path: string; depth: number; sortOrder: number; status: string
  leaderUserId: string | null
  /** ★ null = 未统计（树接口默认不带）。不要渲染成 0。 */
  memberCount: number | null
  children: OrgNode[]
}

const TREE_KEY = 'org-tree'

export default function OrgTreePage() {
  const { message, modal } = App.useApp()
  const qc = useQueryClient()
  const perm = usePerm()
  const [selected, setSelected] = useState<OrgNode | null>(null)

  // ★ 必须传 maxDepth：缺省是 Integer.MAX_VALUE，一次返回整棵树。
  //   3000 节点的嵌套 JSON 一把拉回来 + antd Tree 全量渲染会卡 1–3 秒。
  const tree = useQuery({
    queryKey: [TREE_KEY, 2],
    queryFn: async () => (await apiClient.get('/api/v1/org/units/tree?maxDepth=2')).data.data as OrgNode[],
  })

  const move = useMutation({
    mutationFn: async (v: { orgId: number; newParentId: number }) =>
      apiClient.put(`/api/v1/org/units/${v.orgId}/parent`, { newParentId: v.newParentId }),
    onSuccess: () => {
      message.success('已移动')
      // ★ 只失效树，不要无参 invalidateQueries() —— 那会把万行通讯录一起重拉
      qc.invalidateQueries({ queryKey: [TREE_KEY] })
    },
    onError: (e) => {
      const n = normalizeError(e)
      // 2002 组织成环。后端会拦，但前端也预判一次：拖拽时就该给出禁止指示，
      // 而不是松手之后才弹错。这里是"强行提交"的兜底。
      modal.error({ title: '移动失败', content: n.text })
      qc.invalidateQueries({ queryKey: [TREE_KEY] })   // 回滚到服务端真值
    },
  })

  const nodeIndex = useMemo(() => {
    const m = new Map<number, OrgNode>()
    const walk = (ns: OrgNode[]) => ns.forEach((n) => { m.set(n.id, n); walk(n.children ?? []) })
    walk(tree.data ?? [])
    return m
  }, [tree.data])

  /** 成环预判：不能拖到自己或自己的后代。用 path 前缀判，与后端同一套语义。 */
  const canDrop = (dragId: number, dropId: number) => {
    const drag = nodeIndex.get(dragId); const drop = nodeIndex.get(dropId)
    if (!drag || !drop) return false
    if (dragId === dropId) return false
    return !drop.path.startsWith(drag.path)
  }

  const toTreeData = (ns: OrgNode[]): TreeDataNode[] =>
    ns.map((n) => ({
      key: n.id,
      title: (
        <span>
          {n.name}
          <Typography.Text type="secondary" style={{ marginInlineStart: 8, fontSize: 12 }}>
            {n.code}
          </Typography.Text>
        </span>
      ),
      children: n.children?.length ? toTreeData(n.children) : undefined,
    }))

  if (tree.isLoading) return <><PageHeader title="组织管理" /><PageSkeleton rows={10} /></>
  if (tree.isError) return <><PageHeader title="组织管理" /><ErrorState message={errText(tree.error)} onRetry={tree.refetch} /></>

  const draggable = perm.has('oa:org:move')

  return (
    <>
      <PageHeader
        title="组织管理"
        description={draggable ? '拖拽节点可调整组织归属；不能拖到自己的下级' : '只读：你没有调整组织的权限'}
      />
      <ScopeBanner module="org" />
      <Row gutter={16}>
        <Col xs={24} lg={10}>
          <Card title="组织树" size="small" styles={{ body: { maxHeight: '68vh', overflow: 'auto' } }}>
            <Tree
              treeData={toTreeData(tree.data ?? [])}
              draggable={draggable}
              blockNode
              defaultExpandAll={false}
              onSelect={(keys) => setSelected(keys.length ? nodeIndex.get(Number(keys[0])) ?? null : null)}
              allowDrop={({ dragNode, dropNode }) => canDrop(Number(dragNode.key), Number(dropNode.key))}
              onDrop={(info) => {
                const dragId = Number(info.dragNode.key)
                const dropId = Number(info.node.key)
                if (!canDrop(dragId, dropId)) { message.warning('不能移动到自己的下级组织'); return }
                move.mutate({ orgId: dragId, newParentId: dropId })
              }}
            />
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card title="详情" size="small">
            {selected ? (
              <Descriptions column={{ xs: 1, md: 2 }} size="small" bordered>
                <Descriptions.Item label="名称">{selected.name}</Descriptions.Item>
                <Descriptions.Item label="编码"><span className="mono">{selected.code}</span></Descriptions.Item>
                <Descriptions.Item label="类型"><Tag>{selected.type}</Tag></Descriptions.Item>
                <Descriptions.Item label="层级">{selected.depth}</Descriptions.Item>
                <Descriptions.Item label="路径" span={2}>
                  <span className="mono">{selected.path}</span>
                </Descriptions.Item>
                <Descriptions.Item label="负责人">{selected.leaderUserId ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="成员数">
                  {/* ★ null 表示未统计。渲染成 0 会让人以为这个部门是空的 */}
                  {selected.memberCount == null ? <Typography.Text type="secondary">未统计</Typography.Text> : selected.memberCount}
                </Descriptions.Item>
              </Descriptions>
            ) : (
              <Typography.Text type="secondary">在左侧选择一个组织</Typography.Text>
            )}
          </Card>
        </Col>
      </Row>
    </>
  )
}
