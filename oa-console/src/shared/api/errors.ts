import type { AxiosError } from 'axios'

/**
 * 后端业务码。与 `oa-common/api/ResultCode.java` 一一对应。
 * 0 成功 / 1xxx 通用 / 2xxx 组织 / 3xxx 权限 / 4xxx 流程 / 5xxx 考勤 / 9xxx 系统。
 */
export const Code = {
  SUCCESS: 0,
  BAD_REQUEST: 1400,
  UNAUTHORIZED: 1401,
  FORBIDDEN: 1403,
  NOT_FOUND: 1404,
  CONFLICT: 1409,
  ORG_NOT_FOUND: 2001,
  ORG_CYCLE: 2002,
  ORG_HAS_CHILDREN: 2003,
  EMPLOYEE_NOT_FOUND: 2010,
  PRIMARY_ASSIGNMENT_CONFLICT: 2011,
  PERM_DENIED: 3001,
  PERM_ELEVATION_REQUIRED: 3002,
  GRANT_EXPIRED: 3003,
  DELEGATION_INVALID: 3004,
  DATA_SCOPE_DENIED: 3005,
  IDENTITY_NOT_FOUND: 3010,
  IDENTITY_TYPE_IMMUTABLE: 3011,
  IDENTITY_STATUS_CONFLICT: 3012,
  NHI_OWNER_REQUIRED: 3013,
  AUTHZ_CHECK_INVALID: 3020,
  FLOW_START_FAILED: 4001,
  FLOW_TASK_NOT_FOUND: 4002,
  FORM_TEMPLATE_INVALID: 4003,
  PUNCH_DUPLICATE: 5001,
  LEAVE_BALANCE_INSUFFICIENT: 5002,
  INTERNAL_ERROR: 9000,
  DEPENDENCY_UNAVAILABLE: 9001,
} as const

/**
 * 归一后的错误。
 *
 * <p>★ **HTTP status 与业务 code 是双轨的，只判 status 必错**：
 * 403 里混了 3001/3002/3003/3004/3005，409 里混了 2002/2003/2011/5001。
 * 所有分支判断一律用 `kind`，不要在业务代码里再判 status。
 */
export type ErrorKind =
  | 'unauthorized'      // 未认证 —— 触发续期/跳登录
  | 'forbidden'         // 无权限 —— 不给重试
  | 'needElevation'     // ★ 需 JIT 提权 —— 走提权闭环，不是普通 403
  | 'elevationExpired'  // 提权已过期 —— 重新申请
  | 'conflict'          // 状态冲突 —— 可重试或需刷新
  | 'validation'        // 参数/业务校验 —— 就地红字
  | 'notFound'
  | 'server'            // 5xx / 9xxx —— 中性文案，不吐后端串
  | 'network'           // 请求根本没到服务端

export interface NormalizedError {
  kind: ErrorKind
  code: number
  /** 给用户看的话。已确保不会是后端堆栈。 */
  text: string
  /** 需要提权时，缺的是哪些权限点（从 message 里解析）。 */
  requiredPerms?: string[]
  /** 是否值得给一个"重试"按钮。 */
  retryable: boolean
  raw?: unknown
}

const GENERIC_SERVER = '操作失败，请稍后重试'

export function normalizeError(e: unknown): NormalizedError {
  const err = e as AxiosError<{ code?: number; message?: string }> | undefined

  if (err && !err.response) {
    return { kind: 'network', code: -1, text: '网络不可达，请检查连接', retryable: true, raw: e }
  }

  const status = err?.response?.status ?? 0
  const body = err?.response?.data
  const code = typeof body?.code === 'number' ? body.code : status
  // ★ 只用后端给的 message，不用 err.message（那是 axios 的技术描述）；
  //   5xx 一律换成中性文案，不把后端异常串暴露给用户。
  const backendMsg = body?.message

  switch (code) {
    case Code.UNAUTHORIZED:
      return { kind: 'unauthorized', code, text: '登录已过期，请重新登录', retryable: false, raw: e }

    case Code.PERM_ELEVATION_REQUIRED:
      return {
        kind: 'needElevation',
        code,
        text: backendMsg ?? '该操作需要临时提权',
        requiredPerms: parsePerms(backendMsg),
        retryable: false,
        raw: e,
      }

    case Code.GRANT_EXPIRED:
      return { kind: 'elevationExpired', code, text: '授权已过期，请重新申请', retryable: false, raw: e }

    case Code.PERM_DENIED:
    case Code.FORBIDDEN:
    case Code.DATA_SCOPE_DENIED:
    case Code.DELEGATION_INVALID:
      return { kind: 'forbidden', code, text: backendMsg ?? '你没有执行该操作的权限', retryable: false, raw: e }

    case Code.ORG_CYCLE:
      return { kind: 'conflict', code, text: backendMsg ?? '不能移动到自己的下级组织', retryable: false, raw: e }
    case Code.ORG_HAS_CHILDREN:
      return { kind: 'conflict', code, text: backendMsg ?? '该组织下仍有子组织或成员', retryable: false, raw: e }
    case Code.PRIMARY_ASSIGNMENT_CONFLICT:
      return { kind: 'conflict', code, text: backendMsg ?? '该员工已有主岗', retryable: false, raw: e }
    case Code.PUNCH_DUPLICATE:
      return { kind: 'conflict', code, text: backendMsg ?? '今天已经打过卡了', retryable: false, raw: e }
    case Code.CONFLICT:
      return { kind: 'conflict', code, text: backendMsg ?? '状态已变更，请刷新后重试', retryable: true, raw: e }

    case Code.NOT_FOUND:
    case Code.ORG_NOT_FOUND:
    case Code.EMPLOYEE_NOT_FOUND:
    case Code.FLOW_TASK_NOT_FOUND:
    case Code.IDENTITY_NOT_FOUND:
      return { kind: 'notFound', code, text: backendMsg ?? '资源不存在或已被删除', retryable: false, raw: e }

    case Code.IDENTITY_STATUS_CONFLICT:
      return { kind: 'conflict', code, text: backendMsg ?? '身份状态不允许该操作', retryable: false, raw: e }

    case Code.BAD_REQUEST:
    case Code.FLOW_START_FAILED:
    case Code.FORM_TEMPLATE_INVALID:
    case Code.LEAVE_BALANCE_INSUFFICIENT:
    case Code.IDENTITY_TYPE_IMMUTABLE:
    case Code.NHI_OWNER_REQUIRED:
    case Code.AUTHZ_CHECK_INVALID:
      return { kind: 'validation', code, text: backendMsg ?? '请求参数不合法', retryable: false, raw: e }

    case Code.INTERNAL_ERROR:
    case Code.DEPENDENCY_UNAVAILABLE:
      return { kind: 'server', code, text: GENERIC_SERVER, retryable: true, raw: e }

    default:
      if (status >= 500) return { kind: 'server', code, text: GENERIC_SERVER, retryable: true, raw: e }
      if (status === 401) return { kind: 'unauthorized', code, text: '登录已过期，请重新登录', retryable: false, raw: e }
      if (status === 403) return { kind: 'forbidden', code, text: backendMsg ?? '无权限', retryable: false, raw: e }
      return { kind: 'validation', code, text: backendMsg ?? `请求失败（${status || '未知'}）`, retryable: false, raw: e }
  }
}

/** 从 "该操作需要临时提权，请先申请：oa:audit:view" 里抠出权限点。 */
function parsePerms(msg?: string): string[] | undefined {
  if (!msg) return undefined
  const m = msg.match(/[：:]\s*(.+)$/)
  if (!m) return undefined
  return m[1].split(/[,，、]\s*/).map((s) => s.trim()).filter(Boolean)
}

/** 简写：只要文案。 */
export const errText = (e: unknown) => normalizeError(e).text
