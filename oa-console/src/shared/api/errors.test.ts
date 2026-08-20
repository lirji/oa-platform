import { describe, expect, it } from 'vitest'
import { Code, normalizeError } from './errors'

/** 造一个形如 axios 错误的对象。 */
const httpErr = (status: number, code?: number, message?: string) => ({
  isAxiosError: true,
  response: { status, data: code === undefined ? {} : { code, message } },
})

describe('normalizeError —— 全错误码表', () => {
  it('★ 3002 必须走独立分支，不能被当成普通 403', () => {
    // 这是最重要的一条：3002 的 HTTP status 也是 403。
    // 若归一成 forbidden，用户永远找不到提权入口，还会以为是权限配错了去找管理员加权限
    // （加了还是不行，因为 elevation 是另一条路径）。
    const r = normalizeError(httpErr(403, Code.PERM_ELEVATION_REQUIRED,
      '该操作需要临时提权，请先申请：oa:audit:view'))
    expect(r.kind).toBe('needElevation')
    expect(r.requiredPerms).toEqual(['oa:audit:view'])
  })

  it('3002 能解析出多个权限点', () => {
    const r = normalizeError(httpErr(403, Code.PERM_ELEVATION_REQUIRED,
      '该操作需要临时提权，请先申请：oa:audit:view, oa:employee:export'))
    expect(r.requiredPerms).toEqual(['oa:audit:view', 'oa:employee:export'])
  })

  it.each([
    [Code.PERM_DENIED, 403, 'forbidden'],
    [Code.FORBIDDEN, 403, 'forbidden'],
    [Code.DATA_SCOPE_DENIED, 403, 'forbidden'],
    [Code.DELEGATION_INVALID, 403, 'forbidden'],
    [Code.GRANT_EXPIRED, 403, 'elevationExpired'],
  ])('403 下的 %i 归一为 %s（只判 status 会全部混成一类）', (code, status, kind) => {
    expect(normalizeError(httpErr(status, code)).kind).toBe(kind)
  })

  it.each([
    [Code.ORG_CYCLE, 'conflict'],
    [Code.ORG_HAS_CHILDREN, 'conflict'],
    [Code.PRIMARY_ASSIGNMENT_CONFLICT, 'conflict'],
    [Code.PUNCH_DUPLICATE, 'conflict'],
  ])('409 下的 %i 归一为 %s', (code, kind) => {
    expect(normalizeError(httpErr(409, code)).kind).toBe(kind)
  })

  it('组织成环给的是人话，不是错误码', () => {
    const r = normalizeError(httpErr(409, Code.ORG_CYCLE, '组织移动会形成环'))
    expect(r.text).toBe('组织移动会形成环')
    expect(r.retryable).toBe(false)   // 重试也不会变，不给重试按钮
  })

  it('★ 5xx 不把后端异常串暴露给用户', () => {
    const r = normalizeError(httpErr(500, Code.INTERNAL_ERROR,
      'org.postgresql.util.PSQLException: ERROR: column x does not exist'))
    expect(r.text).not.toContain('PSQLException')
    expect(r.kind).toBe('server')
    expect(r.retryable).toBe(true)
  })

  it('没有 response = 网络层失败，与"服务端拒绝"要分开', () => {
    const r = normalizeError({ isAxiosError: true })
    expect(r.kind).toBe('network')
    expect(r.retryable).toBe(true)
  })

  it('后端没给 code 时退回按 status 判', () => {
    expect(normalizeError(httpErr(401)).kind).toBe('unauthorized')
    expect(normalizeError(httpErr(503)).kind).toBe('server')
    expect(normalizeError(httpErr(404)).kind).toBe('validation')
  })

  it('4xx 校验类错误原样透出后端文案（那是给用户看的）', () => {
    const r = normalizeError(httpErr(400, Code.BAD_REQUEST, '缺少必填字段: amount、reason'))
    expect(r.kind).toBe('validation')
    expect(r.text).toBe('缺少必填字段: amount、reason')
  })

  it('额度不足是业务校验不是系统错误', () => {
    const r = normalizeError(httpErr(400, Code.LEAVE_BALANCE_INSUFFICIENT, '假期额度不足'))
    expect(r.kind).toBe('validation')
  })
})
