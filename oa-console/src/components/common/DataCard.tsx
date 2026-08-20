import type { ReactNode } from 'react'
import { Button, Card, Space } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { EmptyState, ErrorState, PageSkeleton } from './AsyncState'
import { errText } from '@oa/shared/api/errors'
import { ScopeEmpty } from './ScopeHint'

interface DataCardProps<T> {
  title?: ReactNode
  extra?: ReactNode
  /** react-query 的三态。刻意要 `isLoading` 而不是 `isFetching`（后者会在后台刷新时遮住旧数据）。 */
  query: { isLoading: boolean; isError: boolean; error: unknown; isFetching: boolean; refetch: () => void }
  data: T[] | undefined
  /** 空态是否受数据权限影响。true 时用 ScopeEmpty 而不是普通"暂无数据"。 */
  scoped?: boolean
  scopeModule?: string
  emptyText?: string
  children: (rows: T[]) => ReactNode
}

/**
 * 列表页四态骨架。家族约定：
 * `isLoading → Skeleton` / `isError → ErrorState + 重试` / `空 → Empty` / `else → 内容`。
 *
 * <p>error **不叠 toast** —— 曾经 `onError` toast + Result 双重反馈，后来移除了。
 */
export function DataCard<T>({
  title, extra, query, data, scoped, scopeModule, emptyText, children,
}: DataCardProps<T>) {
  let body: ReactNode
  if (query.isLoading) body = <PageSkeleton />
  else if (query.isError) body = <ErrorState message={errText(query.error)} onRetry={query.refetch} />
  else if (!data || data.length === 0) {
    body = scoped ? <ScopeEmpty module={scopeModule} /> : <EmptyState description={emptyText} />
  } else body = children(data)

  return (
    <Card
      title={title}
      extra={
        <Space>
          {extra}
          <Button
            type="text" icon={<ReloadOutlined />} loading={query.isFetching}
            onClick={() => query.refetch()} aria-label="刷新"
          />
        </Space>
      }
    >
      {body}
    </Card>
  )
}
