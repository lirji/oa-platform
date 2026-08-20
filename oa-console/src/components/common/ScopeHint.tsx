import { Alert, Empty, Tag, Tooltip } from 'antd'
import { InfoCircleOutlined } from '@ant-design/icons'
import { emptyHint } from '@oa/shared/perm/evaluate'
import { usePerm } from '../../auth/usePerm'

/**
 * 数据范围提示。
 *
 * <p>★★★ 这是本项目前端**最重要的一个小组件**。
 *
 * <p>后端的数据权限不足时<b>不抛错</b>：`@DataScope` 算不出范围就生成 `1 = 0`，
 * 接口返回 **HTTP 200 + 空数组**（`DATA_SCOPE_DENIED(3005)` 在整个后端从未被抛出过）。
 * 于是"你无权看这些行"与"确实一行都没有"在前端<b>完全无法区分</b>。
 *
 * <p>不显式提示的话，SELF 范围的人打开员工列表会看到"暂无数据"，
 * 然后合理地以为**公司真的只有他一个人** —— 系统看起来完全正常，结论却是错的。
 */
export function ScopeEmpty({ module }: { module?: string }) {
  const perm = usePerm()
  const scope = module ? perm.scopeOf(module) : perm.dataScope
  return (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={
        <span>
          {emptyHint(scope, perm.scopePrefixes.length)}
          {scope !== 'ALL' && (
            <Tooltip title={perm.scopePrefixes.length ? perm.scopePrefixes.join('\n') : '没有任何可见范围'}>
              <Tag icon={<InfoCircleOutlined />} style={{ marginInlineStart: 8 }}>查看范围</Tag>
            </Tooltip>
          )}
        </span>
      }
    />
  )
}

/** 列表顶部的范围条。非 ALL 时常驻显示——让"我看到的是全部还是一部分"永远可见。 */
export function ScopeBanner({ module }: { module?: string }) {
  const perm = usePerm()
  const scope = module ? perm.scopeOf(module) : perm.dataScope
  if (scope === 'ALL') return null
  return (
    <Alert
      type="info"
      showIcon
      style={{ marginBottom: 12 }}
      message={`当前数据范围：${perm.dataScopeLabel}${
        perm.scopePrefixes.length ? `（${perm.scopePrefixes.length} 个组织范围）` : ''
      }`}
      description="列表只显示范围内的数据。看不到某条记录不代表它不存在。"
    />
  )
}
