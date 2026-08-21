import { useEffect, useMemo, useState } from 'react'
import {
  Alert, AutoComplete, Badge, Button, Card, Col, Descriptions, Empty, Input,
  Row, Space, Statistic, Tag, Timeline, Tooltip, Typography,
} from 'antd'
import { ExperimentOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'
import { PageHeader } from '../../components/layout/PageHeader'
import { PageSkeleton, ErrorState } from '../../components/common/AsyncState'
import { useSearchParams } from 'react-router-dom'
import { colors } from '../../theme/colors'
import { useAppBreakpoint } from '../../hooks/useAppBreakpoint'
import { usePermVersion } from '../../auth/usePerm'

/** 一条授权来源。后端 `/iam/admin/why` 返回，已是 camelCase 类型化 record。 */
interface PermSource {
  grantId: number; subjectType: string; subjectId: string
  roleCode: string; roleName: string; roleDistance: number
  grantType: string; scopeType: string; includeDescendants: boolean
  validTo: string | null; reason: string | null
  grantedBy: string; grantedAt: string
  /** 人话：「所在组织「事业部-16」被授权（含下级，按 org_path 前缀继承）」 */
  via: string
  /** 人话：「经角色继承（向上 2 层）获得」 */
  rolePath: string
}

/**
 * 权限沙盘（FINAL_PLAN §16 的"首个可演示里程碑"）。
 *
 * <p>三栏：左选人 / 中解释"为什么" / 右预览"他看到什么"。
 * 一个页面同时证明 G1（组织模型）、G2（权限体系）、G3（秒级生效）。
 *
 * <p>★ 三栏状态**不进全局 store** —— 它是局部、短命、强耦合的。
 * 选中态放 `useSearchParams`：沙盘状态可分享、可刷新恢复，演示时直接贴链接。
 */
export default function SandboxPage() {
  const permVersion = usePermVersion()
  const [sp, setSp] = useSearchParams()
  const bp = useAppBreakpoint()
  const userId = sp.get('userId') ?? ''
  const permCode = sp.get('permCode') ?? 'oa:employee:view'
  const [draftUser, setDraftUser] = useState(userId)

  // C 档（1280–1439，1366×768 落这里）：右栏收起为可展开面板
  // C 档起右栏收起为可展开面板（B 档 1440–1599 仍是三栏并排 ——
  // 原来的 `!screens.xxl` 把 B 档也算成窄屏了，见 useAppBreakpoint 的注释）。
  const narrow = bp.inspectorMode !== 'inline'
  // 初值仍取 false + useEffect 跟随：hook 首帧就给出正确档位，
  // 这个 effect 只负责"用户没手动切过时跟着档位走"这一条语义。
  const [inspectorOpen, setInspectorOpen] = useState(false)
  const [userToggled, setUserToggled] = useState(false)
  useEffect(() => {
    if (!userToggled) setInspectorOpen(!narrow)
  }, [narrow, userToggled])

  const setParam = (k: string, v: string) => {
    const next = new URLSearchParams(sp)
    if (v) next.set(k, v); else next.delete(k)
    setSp(next, { replace: true })
  }

  const why = useQuery({
    queryKey: ['sandbox-why', userId, permCode, permVersion],
    queryFn: async () =>
      (await apiClient.get(`/api/v1/iam/admin/why?userId=${encodeURIComponent(userId)}&permCode=${encodeURIComponent(permCode)}`)).data.data,
    enabled: Boolean(userId && permCode),
    staleTime: 0,   // 沙盘要的就是最新
  })

  const explain = useQuery({
    queryKey: ['sandbox-explain', userId, permCode, permVersion],
    queryFn: async () =>
      (await apiClient.get(`/api/v1/iam/admin/explain?userId=${encodeURIComponent(userId)}&permCode=${encodeURIComponent(permCode)}`)).data.data,
    enabled: Boolean(userId),
    staleTime: 0,
  })

  const preview = useQuery({
    queryKey: ['sandbox-preview', userId, permVersion],
    queryFn: async () =>
      (await apiClient.get(`/api/v1/iam/admin/preview?userId=${encodeURIComponent(userId)}`)).data.data,
    enabled: Boolean(userId) && inspectorOpen,
    staleTime: 0,
  })

  const catalog = useQuery({
    queryKey: ['perm-catalog'],
    queryFn: async () => (await apiClient.get('/api/v1/iam/permissions/catalog')).data.data as
      { code: string; name: string; type: string; enabled: boolean }[],
    staleTime: 5 * 60_000,
  })

  /**
   * ★ 来源链去重折叠。
   * 角色继承闭包展开后，同一个角色可能出现十几条同源授权 ——
   * 不折叠的话中栏是十几行几乎一样的噪声，**恰恰盖住了"部门继承"这条最有说服力的链路**。
   */
  const grouped = useMemo(() => {
    const src = (why.data?.sources ?? []) as PermSource[]
    const m = new Map<string, { head: PermSource; count: number }>()
    for (const s of src) {
      const k = `${s.roleCode}|${s.via}|${s.scopeType}|${s.grantType}`
      const cur = m.get(k)
      if (cur) cur.count += 1
      else m.set(k, { head: s, count: 1 })
    }
    // 组织继承排前面 —— 它是最值得看的那条
    return [...m.values()].sort((a, b) =>
      (a.head.subjectType === 'ORG_UNIT' ? -1 : 0) - (b.head.subjectType === 'ORG_UNIT' ? -1 : 0))
  }, [why.data])

  return (
    <>
      <PageHeader
        title={<Space><ExperimentOutlined />权限沙盘</Space>}
        description="选一个人和一条权限，看它是怎么来的、以及这个人到底能看到什么"
        extra={
          <Space>
            {narrow && (
              <Button onClick={() => { setUserToggled(true); setInspectorOpen((v) => !v) }}>
                {inspectorOpen ? '收起预览' : '展开预览'}
              </Button>
            )}
            <BenchButton userId={userId} permCode={permCode} />
          </Space>
        }
      />

      <Row gutter={16}>
        {/* ── 左栏：选人 + 选权限点 ── */}
        <Col xs={24} lg={inspectorOpen && !narrow ? 6 : 8}>
          <Card size="small" title="观察对象">
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              <Input.Search
                placeholder="用户 id（如 seed-user-10000）"
                value={draftUser}
                onChange={(e) => setDraftUser(e.target.value)}
                onSearch={(v) => setParam('userId', v.trim())}
                enterButton="观察"
              />
              <AutoComplete
                style={{ width: '100%' }}
                value={permCode}
                onChange={(v) => setParam('permCode', v)}
                placeholder="权限点，如 oa:employee:view"
                options={(catalog.data ?? [])
                  .filter((p) => p.type === 'API' && p.enabled)
                  .map((p) => ({ value: p.code, label: `${p.code} · ${p.name}` }))}
                filterOption={(input, opt) => String(opt?.value ?? '').includes(input)}
              />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                目录里没有的 code 一律判为拒绝（fail-closed）——
                打错字的表现就是"永远没有权限"。
              </Typography.Text>
            </Space>
          </Card>
        </Col>

        {/* ── 中栏：为什么 ── */}
        <Col xs={24} lg={inspectorOpen && !narrow ? 10 : 16}>
          <Card size="small" title="判定与来源链">
            {!userId ? (
              <Empty description="先在左侧输入一个用户 id" />
            ) : why.isLoading ? (
              <PageSkeleton rows={6} />
            ) : why.isError ? (
              <ErrorState message={errText(why.error)} onRetry={why.refetch} />
            ) : (
              <>
                {/* ★ consistent=false 用顶部 Alert 而不是整栏染红 —— 染红会盖住来源链本身 */}
                {explain.data && explain.data.consistent === false && (
                  <Alert
                    type="error" showIcon style={{ marginBottom: 12 }}
                    message="缓存与数据库不一致"
                    description={`缓存 ${explain.data.cachedPermCount} 条 / 重算 ${explain.data.truthPermCount} 条。
                      通常意味着有人绕过失效协议直接改了库，或跨节点通知丢了。`}
                  />
                )}
                <Space wrap style={{ marginBottom: 16 }}>
                  <Tag color={why.data.allowed ? 'success' : 'default'}>
                    {why.data.allowed ? '允许' : '拒绝'}
                  </Tag>
                  {!why.data.known && <Tag color="error">目录中无此 code</Tag>}
                  {why.data.currentlyElevated && <Tag color="warning">当前处于提权状态</Tag>}
                  {explain.data && (
                    <Tag color={explain.data.consistent ? 'success' : 'error'}>
                      影子校验 {explain.data.consistent ? '一致' : '不一致'}
                    </Tag>
                  )}
                </Space>

                {grouped.length === 0 ? (
                  <Empty description={why.data.reason ?? '没有任何授权能推出这条权限'} />
                ) : (
                  <Timeline
                    items={grouped.map(({ head, count }) => ({
                      color: head.subjectType === 'ORG_UNIT' ? colors.primary
                        : head.grantType === 'TEMPORARY' ? colors.warning : colors.success,
                      children: (
                        <Space direction="vertical" size={2}>
                          <Typography.Text strong>{head.via}</Typography.Text>
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {head.roleName}（{head.roleCode}）· {head.rolePath}
                          </Typography.Text>
                          <Space size={6} wrap>
                            <Tag>{head.scopeType}</Tag>
                            <Tag color={head.grantType === 'TEMPORARY' ? 'warning' : 'default'}>
                              {head.grantType}
                            </Tag>
                            {head.validTo && <Tag color="warning">到期 {new Date(head.validTo).toLocaleString('zh-CN')}</Tag>}
                            {count > 1 && (
                              <Tooltip title="角色继承闭包展开后的同源授权，已折叠">
                                <Tag>另有 {count - 1} 条同源</Tag>
                              </Tooltip>
                            )}
                          </Space>
                          {head.reason && (
                            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                              事由：{head.reason}
                            </Typography.Text>
                          )}
                        </Space>
                      ),
                    }))}
                  />
                )}
              </>
            )}
          </Card>
        </Col>

        {/* ── 右栏：他看到什么 ── */}
        {inspectorOpen && (
          <Col xs={24} lg={narrow ? 24 : 8}>
            <Card size="small" title="该员工看到的">
              {!userId ? <Empty description="—" />
                : preview.isLoading ? <PageSkeleton rows={5} />
                : preview.isError ? <ErrorState message={errText(preview.error)} onRetry={preview.refetch} />
                : (
                  <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    <Row gutter={8}>
                      <Col span={12}><Statistic title="权限点" value={preview.data.permCount} /></Col>
                      <Col span={12}><Statistic title="菜单" value={preview.data.menus?.length ?? 0} /></Col>
                    </Row>
                    <Descriptions size="small" column={1} bordered>
                      <Descriptions.Item label="数据范围">
                        <Tag color={preview.data.dataScope === 'ALL' ? 'success' : 'default'}>
                          {preview.data.dataScope}
                        </Tag>
                        {(preview.data.scopePrefixes ?? []).length > 0 && (
                          <Tooltip title={(preview.data.scopePrefixes as string[]).join('\n')}>
                            <Tag>{preview.data.scopePrefixes.length} 个范围</Tag>
                          </Tooltip>
                        )}
                      </Descriptions.Item>
                      <Descriptions.Item label="可见菜单">
                        <Space wrap size={4}>
                          {(preview.data.menus ?? []).map((m: { code: string; name: string }) => (
                            <Tag key={m.code}>{m.name}</Tag>
                          ))}
                        </Space>
                      </Descriptions.Item>
                      {(preview.data.delegators ?? []).length > 0 && (
                        <Descriptions.Item label="代理">
                          {(preview.data.delegators as string[]).join('、')}
                        </Descriptions.Item>
                      )}
                    </Descriptions>
                    <VisibleRows userId={userId} />
                  </Space>
                )}
            </Card>
          </Col>
        )}
      </Row>
    </>
  )
}

/** 「能查到的数据行数」—— 数据权限差异最直观的观测点。 */
function VisibleRows({ userId }: { userId: string }) {
  const permVersion = usePermVersion()
  const q = useQuery({
    queryKey: ['sandbox-visible', userId, permVersion],
    // 用 X-OA-User 以他人身份查可见人数。★ 这条只在 DEV 模式有效，
    // JWT 模式下后端忽略该头 —— 届时应由后端把 visibleCount 并进 /preview。
    queryFn: async () =>
      (await apiClient.get('/api/v1/org/directory/count', { headers: { 'X-OA-User': userId } })).data.data,
    enabled: Boolean(userId),
    staleTime: 0,
    retry: 0,
  })
  return (
    <Statistic
      title={<Space size={4}>能查到的人数<Badge status="processing" /></Space>}
      value={q.data?.visible ?? '—'}
      loading={q.isLoading}
    />
  )
}

/** 现场实测判权 P99 —— "判权 P99 < 1ms" 这条验收标准的按钮版。 */
function BenchButton({ userId, permCode }: { userId: string; permCode: string }) {
  const permVersion = usePermVersion()
  const [result, setResult] = useState<string | null>(null)
  const q = useQuery({
    queryKey: ['sandbox-bench', userId, permCode, permVersion],
    queryFn: async () => {
      const { data } = await apiClient.get(
        `/api/v1/iam/admin/bench?userId=${encodeURIComponent(userId)}&permCode=${encodeURIComponent(permCode)}&iterations=50000`)
      const d = data.data
      setResult(`p50 ${d.p50Us}µs · p99 ${d.p99Us}µs`)
      return d
    },
    enabled: false,
  })
  return (
    <Space>
      {result && <Tag color="success">{result}</Tag>}
      <Button icon={<ThunderboltOutlined />} disabled={!userId} loading={q.isFetching} data-testid="primary-action"
        onClick={() => void q.refetch()}>
        实测判权耗时
      </Button>
    </Space>
  )
}
