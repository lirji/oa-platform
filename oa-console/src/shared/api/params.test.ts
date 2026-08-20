import { describe, expect, it } from 'vitest'
import { apiClient } from './client'

/**
 * 计划 §10 单测 6：查询参数序列化。
 *
 * <p>为什么值得单独钉一条：这个项目在 bash 侧栽过 ——「中文不要拼进 URL 查询串」
 * 是写进 CLAUDE.md 的一条纪律（请求到不了服务端，且响应常被丢弃，极难排查）。
 * 浏览器侧 axios 会正确地做 percent-encoding，但**前提是把值交给 axios 的 `params`**，
 * 而不是自己拼字符串。这条测试钉住的是 axios 的实际行为，
 * 以便将来有人加 `paramsSerializer` 时能立刻看出改变了什么。
 */
const uri = (params: Record<string, unknown>) => apiClient.getUri({ url: '/api/v1/doc/search', params })

describe('查询参数序列化', () => {
  it('中文被 percent-encode（UTF-8）', () => {
    expect(uri({ keyword: '放假' })).toContain('keyword=%E6%94%BE%E5%81%87')
  })

  it('undefined 不拼进 query', () => {
    const u = uri({ keyword: '放假', cursor: undefined })
    expect(u).not.toContain('cursor')
  })

  it('null 也不拼进 query', () => {
    expect(uri({ a: null, b: 1 })).not.toContain('a=')
  })

  it('★ 0 和 false 必须保留（它们不是"空"）', () => {
    // 用 `if (v)` 过滤参数是最常见的写法，而它会把 page=0 和 flag=false 一起吃掉：
    // 表现是"第一页永远拿不到"或"筛选开关点了没反应"。
    const u = uri({ page: 0, flag: false })
    expect(u).toContain('page=0')
    expect(u).toContain('flag=false')
  })

  it('URL 保留字被转义（& # + 不会截断 query）', () => {
    const u = uri({ keyword: 'a&b#c+d' })
    expect(u).toContain('keyword=a%26b%23c%2Bd')
    // 只应该有一个 ? 和一个参数分隔符都不该出现
    expect(u.split('?')[1].split('&')).toHaveLength(1)
  })

  it('空字符串保留（"清空筛选"与"没传筛选"是两件事）', () => {
    expect(uri({ keyword: '' })).toContain('keyword=')
  })
})
