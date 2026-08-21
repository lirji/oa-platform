import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Avatar, Card, Input, Space, Tag, Typography } from 'antd'
import { UserOutlined } from '@ant-design/icons'
import { useVirtualizer } from '@tanstack/react-virtual'
import { PageHeader } from '../../components/layout/PageHeader'
import { ScopeBanner } from '../../components/common/ScopeHint'
import { PageSkeleton, ErrorState, EmptyState } from '../../components/common/AsyncState'
import { useDirectorySync } from '../../hooks/useDirectorySync'

/**
 * 万人通讯录。
 *
 * <p>用 `@tanstack/react-virtual` 而不是 antd Table 的 virtual：这一页是
 * 头像卡片行 + 可变高度，且它与已用的 react-query 同家族；
 * 更重要的是**它有 Vue 版本** —— Phase 6 的 H5 若换渲染层，这层逻辑不作废。
 */
export default function DirectoryPage() {
  const [keyword, setKeyword] = useState('')
  const sync = useDirectorySync()
  const parentRef = useRef<HTMLDivElement>(null)

  // ★ 搜索在本地索引上做，不打后端：万人表每次 keystroke 都请求一次是不可接受的。
  //   debounce 200ms + 本地过滤，瓶颈在 filter 而不在虚拟滚动。
  const [debounced, setDebounced] = useState('')
  useEffect(() => {
    const t = setTimeout(() => setDebounced(keyword.trim()), 200)
    return () => clearTimeout(t)
  }, [keyword])

  const rows = useMemo(() => {
    if (!debounced) return sync.entries
    const kw = debounced.toLowerCase()
    return sync.entries.filter(
      (e) => e.name.toLowerCase().includes(kw) || e.empNo?.toLowerCase().includes(kw)
        || (e.orgName ?? '').toLowerCase().includes(kw),
    )
  }, [sync.entries, debounced])

  const virt = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 64,
    overscan: 12,
  })

  return (
    <>
      <PageHeader
        title="通讯录"
        description={
          <Space size={6}>
            <Typography.Text type="secondary">{sync.entries.length} 人</Typography.Text>
            {sync.lastSyncedAt && (
              // ★ 不显示陈旧时间的离线缓存 = 骗人。这条必须常驻。
              <Tag color={sync.stale ? 'warning' : 'default'}>
                数据更新于 {relative(sync.lastSyncedAt)}
              </Tag>
            )}
            {sync.degraded && <Tag color="warning">本地缓存不可用，已降级为直连</Tag>}
          </Space>
        }
        extra={<Input.Search allowClear placeholder="姓名 / 工号 / 部门" style={{ width: 280 }}
          data-testid="primary-action"
          value={keyword} onChange={(e) => setKeyword(e.target.value)} />}
      />
      <ScopeBanner module="org" />
      {sync.error && <Alert type="warning" showIcon style={{ marginBottom: 12 }}
        message="同步失败，正在展示本地缓存" description={sync.error} />}

      <Card size="small" styles={{ body: { padding: 0 } }}>
        {sync.loading && sync.entries.length === 0 ? (
          <div style={{ padding: 24 }}><PageSkeleton rows={8} /></div>
        ) : sync.fatal ? (
          <div style={{ padding: 24 }}><ErrorState message={sync.fatal} onRetry={sync.resync} /></div>
        ) : rows.length === 0 ? (
          <div style={{ padding: 24 }}><EmptyState description={debounced ? '没有匹配的人' : undefined} /></div>
        ) : (
          <div ref={parentRef} style={{ height: '64vh', overflow: 'auto' }}>
            <div style={{ height: virt.getTotalSize(), position: 'relative' }}>
              {virt.getVirtualItems().map((v) => {
                const e = rows[v.index]
                return (
                  <div
                    key={e.employeeId}
                    // ★ 虚拟列表行必须打 data-testid：DOM 会被回收，
                    //   e2e 靠可见文本定位会随机失败。
                    data-testid="dir-row"
                    style={{
                      position: 'absolute', top: 0, left: 0, width: '100%',
                      height: v.size, transform: `translateY(${v.start}px)`,
                      display: 'flex', alignItems: 'center', gap: 12,
                      padding: '0 16px', borderBottom: '1px solid #E6EAF0',
                    }}
                  >
                    <Avatar icon={<UserOutlined />} />
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <div style={{ fontWeight: 500 }}>{e.name}</div>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }} ellipsis>
                        {e.orgName ?? '—'} · {e.positionName ?? '—'}
                      </Typography.Text>
                    </div>
                    <span className="mono" style={{ color: '#98A2B3' }}>{e.empNo}</span>
                  </div>
                )
              })}
            </div>
          </div>
        )}
      </Card>
    </>
  )
}

function relative(ts: number) {
  const m = Math.round((Date.now() - ts) / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  return `${Math.round(m / 60)} 小时前`
}
