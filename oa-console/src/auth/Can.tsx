import type { ReactNode } from 'react'
import { Tooltip } from 'antd'
import { cloneElement, isValidElement } from 'react'
import { usePerm } from './usePerm'

interface CanProps {
  /** 需要的权限点。多个时默认 AND。 */
  code: string | string[]
  /** 多个权限点时用 any 而不是 all。 */
  any?: boolean
  /**
   * 没权限时怎么办：
   * - `hide`（默认）：什么都不渲染
   * - `disable`：渲染子元素但置灰，并用 Tooltip 说明缺哪个权限
   */
  mode?: 'hide' | 'disable'
  fallback?: ReactNode
  children: ReactNode
}

/**
 * 按钮级权限裁剪。
 *
 * <p>★ **这只是体验层，不是安全边界**（ADR-0008）。后端 `@RequiresPerm` 才是。
 * Phase 8 的渗透用例专门验证过"前端藏了按钮、后端依然 403"。
 *
 * <p>不做跳转 —— 跳转是 `<PermRoute>` 的事。一个按钮没权限就把整页跳走，
 * 会让用户莫名其妙地离开当前上下文。
 */
export default function Can({ code, any = false, mode = 'hide', fallback = null, children }: CanProps) {
  const perm = usePerm()
  const codes = Array.isArray(code) ? code : [code]
  const allowed = any ? perm.hasAny(codes) : perm.hasAll(codes)

  if (allowed) return <>{children}</>
  if (mode === 'hide') return <>{fallback}</>

  // disable 模式：保留元素但禁用，并说明原因。
  // 比直接隐藏更友好的场景是"这个操作存在但你不能做"，隐藏会让用户以为功能不存在。
  const tip = `需要权限：${codes.join(any ? ' 或 ' : ' 且 ')}`
  if (isValidElement(children)) {
    return (
      <Tooltip title={tip}>
        <span style={{ display: 'inline-block', cursor: 'not-allowed' }}>
          {cloneElement(children as React.ReactElement<{ disabled?: boolean }>, { disabled: true })}
        </span>
      </Tooltip>
    )
  }
  return <Tooltip title={tip}><span>{children}</span></Tooltip>
}
