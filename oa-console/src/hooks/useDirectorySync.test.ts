import 'fake-indexeddb/auto'
import { openDB } from 'idb'
import { renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { usePermStore } from '../store/permStore'
import type { MyPermissions } from '@oa/shared/perm/types'
import { merge, persist, useDirectorySync, type DirEntry } from './useDirectorySync'

/**
 * 计划 §10 单测 4：本地缓存层。
 *
 * <p>这一层的错误有个共同点：**界面看起来完全正常**。
 * 墓碑没处理 → 本地一直躺着已离职的人；归属没校验 → 被降权的人
 * 还能从缓存里看到全公司名单（一次完全发生在客户端、后端日志上毫无痕迹的越权）。
 */
const get = vi.hoisted(() => vi.fn())
vi.mock('@oa/shared/api/client', () => ({ apiClient: { get }, onPermVersionChanged: () => {} }))

const base: MyPermissions = {
  userId: 'u-1', username: 'alice', employeeId: 1, primaryOrgId: 1, primaryOrgPath: '/1/',
  version: 1, permCodes: ['oa:employee:view'], menus: [], dataScope: 'ALL', scopePrefixes: ['/'],
  delegators: [], elevatedCodes: [], moduleScope: {},
}
const login = (over: Partial<MyPermissions> = {}) => {
  usePermStore.getState().reset()
  usePermStore.getState().applyPermissions({ ...base, ...over })
}

const emp = (id: number, name: string): DirEntry => ({
  employeeId: id, userId: `u-${id}`, empNo: `E${id}`, name, orgName: '研发', positionName: '工程师',
})

/** 一页全量响应。 */
const page = (items: DirEntry[], hasMore = false, watermark = 100) =>
  ({ data: { data: { items, hasMore, nextCursor: items[items.length - 1]?.employeeId, watermark } } })

const delta = (changes: DirEntry[], deletions: number[] = [], over: Record<string, unknown> = {}) =>
  ({ data: { data: { changes, deletions, hasMore: false, nextSince: 200, watermark: 200, ...over } } })

async function readCache() {
  const db = await openDB('oa-directory', 1)
  const rows = (await db.getAll('entries')) as DirEntry[]
  const meta = await db.get('meta', 'meta')
  db.close()
  return { rows, meta }
}

beforeEach(async () => {
  get.mockReset()
  // 清空而不是 deleteDatabase：删库会被任何一个还开着的连接 block 住，
  // 而 blocked 的删除请求会让后续的 open 一起挂死 —— 表现是用例整体超时，
  // 且第一个用例总是通过（那时还没有别的连接）。
  const db = await openDB('oa-directory', 1, {
    upgrade(d) {
      if (!d.objectStoreNames.contains('entries')) d.createObjectStore('entries', { keyPath: 'employeeId' })
      if (!d.objectStoreNames.contains('meta')) d.createObjectStore('meta')
    },
  })
  await db.clear('entries')
  await db.clear('meta')
  db.close()
})
afterEach(() => usePermStore.getState().reset())

// ─────────────────────────────────────────── 纯函数

describe('merge（增量合并）', () => {
  const b = [emp(1, '甲'), emp(2, '乙')]
  it('变更覆盖同 id', () => {
    expect(merge(b, [{ ...emp(2, '乙改') }], []).find((e) => e.employeeId === 2)?.name).toBe('乙改')
  })
  it('新增追加', () => {
    expect(merge(b, [emp(3, '丙')], []).map((e) => e.employeeId).sort()).toEqual([1, 2, 3])
  })
  it('★ 墓碑真的删掉（离职是"行消失"不是"行变化"）', () => {
    expect(merge(b, [], [1]).map((e) => e.employeeId)).toEqual([2])
  })
  it('同一批里既变更又删除，删除生效', () => {
    expect(merge(b, [emp(2, '乙改')], [2]).map((e) => e.employeeId)).toEqual([1])
  })
  it('删除不存在的 id 不炸', () => {
    expect(merge(b, [], [999])).toHaveLength(2)
  })
})

// ─────────────────────────────────────────── 全量 / 落盘

describe('全量同步', () => {
  it('分页拉到 hasMore=false，并落盘', async () => {
    login()
    get.mockResolvedValueOnce(page([emp(1, '甲')], true, 0))
       .mockResolvedValueOnce(page([emp(2, '乙')], false, 100))
    const { result } = renderHook(() => useDirectorySync())
    await waitFor(() => expect(result.current.entries).toHaveLength(2))
    const { rows, meta } = await readCache()
    expect(rows.map((r) => r.employeeId).sort()).toEqual([1, 2])
    expect(meta).toMatchObject({ ownerId: 'u-1', permVersion: 1, since: 100 })
  })

  it('★ 敏感字段不落盘', async () => {
    login()
    // 后端即使返回了手机号，也不许写进磁盘
    get.mockResolvedValueOnce(page([{ ...emp(1, '甲'), mobile: '13800000000' } as DirEntry]))
    const { result } = renderHook(() => useDirectorySync())
    await waitFor(() => expect(result.current.entries).toHaveLength(1))
    const { rows } = await readCache()
    expect(rows[0]).not.toHaveProperty('mobile')
    expect(JSON.stringify(rows)).not.toContain('13800000000')
  })
})

// ─────────────────────────────────────────── 归属校验（最要紧的一条）

describe('缓存归属 (userId, permVersion)', () => {
  it('★ 权限版本变了 → 缓存整体作废，重新全量', async () => {
    login({ version: 1 })
    get.mockResolvedValueOnce(page([emp(1, '甲'), emp(2, '乙')]))
    const first = renderHook(() => useDirectorySync())
    await waitFor(() => expect(first.result.current.entries).toHaveLength(2))
    first.unmount()

    // 被降权：新版本下只看得到 1 个人。若缓存没作废，他还能看到 2 个。
    login({ version: 2 })
    get.mockReset()
    get.mockResolvedValueOnce(page([emp(1, '甲')], false, 300))
    const second = renderHook(() => useDirectorySync())
    await waitFor(() => expect(second.result.current.entries).toHaveLength(1))
    // 走的是全量（/page）而不是增量（/delta）
    expect(get.mock.calls[0][0]).toContain('/directory/page')
    const { rows } = await readCache()
    expect(rows).toHaveLength(1)
  })

  it('换了人 → 缓存作废', async () => {
    login({ userId: 'u-1' })
    get.mockResolvedValueOnce(page([emp(1, '甲')]))
    const a = renderHook(() => useDirectorySync())
    await waitFor(() => expect(a.result.current.entries).toHaveLength(1))
    a.unmount()

    login({ userId: 'u-2' })
    get.mockReset()
    get.mockResolvedValueOnce(page([emp(9, '别人')]))
    const b = renderHook(() => useDirectorySync())
    await waitFor(() => expect(b.result.current.entries).toHaveLength(1))
    expect(get.mock.calls[0][0]).toContain('/directory/page')
    expect(b.result.current.entries[0].employeeId).toBe(9)
  })
})

// ─────────────────────────────────────────── 增量

describe('增量同步', () => {
  async function seedCache() {
    login()
    get.mockResolvedValueOnce(page([emp(1, '甲'), emp(2, '乙')], false, 100))
    const h = renderHook(() => useDirectorySync())
    await waitFor(() => expect(h.result.current.entries).toHaveLength(2))
    h.unmount()
    get.mockReset()
  }

  it('同一版本下第二次进入走 /delta，并应用变更与墓碑', async () => {
    await seedCache()
    get.mockResolvedValueOnce(delta([emp(3, '丙')], [1]))
    const { result } = renderHook(() => useDirectorySync())
    await waitFor(() => expect(result.current.entries.map((e) => e.employeeId).sort()).toEqual([2, 3]))
    expect(get.mock.calls[0][0]).toContain('/directory/delta?since=100')
    const { rows, meta } = await readCache()
    expect(rows.map((r) => r.employeeId).sort()).toEqual([2, 3])
    expect(meta.since).toBe(200)
  })

  it('fullResync 逃生舱 → 退回全量', async () => {
    await seedCache()
    get.mockResolvedValueOnce(delta([], [], { fullResync: true }))
       .mockResolvedValueOnce(page([emp(7, '重来')], false, 500))
    const { result } = renderHook(() => useDirectorySync())
    await waitFor(() => expect(result.current.entries).toHaveLength(1))
    expect(get.mock.calls[1][0]).toContain('/directory/page')
  })

  it('★ 增量中途失败不推进水位线', async () => {
    await seedCache()
    get.mockRejectedValueOnce(new Error('网络断了'))
    const { result } = renderHook(() => useDirectorySync())
    await waitFor(() => expect(result.current.error).toBeTruthy())
    const { meta } = await readCache()
    // 推进了的话，中间那段变更会被**永久跳过** —— 不会报错，只是有些人的信息永远不更新
    expect(meta.since).toBe(100)
  })

  it('同步失败但有缓存 → 只是 error，不是 fatal', async () => {
    await seedCache()
    get.mockRejectedValueOnce(new Error('网络断了'))
    const { result } = renderHook(() => useDirectorySync())
    await waitFor(() => expect(result.current.error).toBeTruthy())
    expect(result.current.entries).toHaveLength(2)   // 旧数据继续可用
    expect(result.current.fatal).toBeNull()
  })
})

// ─────────────────────────────────────────── 写盘失败

describe('落盘失败的处理', () => {
  const meta = { since: 1, ownerId: 'u-1', permVersion: 1, syncedAt: 0 }
  /** 造一个事务一开就抛的 db。 */
  const throwingDb = (err: unknown) => ({ transaction: () => { throw err } }) as never

  it('★ 配额超限 → 降级为纯网络，不再往上抛', async () => {
    // 抛出去的话整次同步会被判为失败，用户看到的是"同步出错"而不是"缓存不可用"，
    // 而重试只会一次次撞同一堵墙。
    const onQuota = vi.fn()
    const quota = Object.assign(new Error('quota'), { name: 'QuotaExceededError' })
    await expect(persist(throwingDb(quota), [emp(1, '甲')], [], meta, onQuota)).resolves.toBeUndefined()
    expect(onQuota).toHaveBeenCalledWith(true)
  })

  it('其它写盘错误照常抛（别把真 bug 也当成配额问题吞掉）', async () => {
    const onQuota = vi.fn()
    await expect(persist(throwingDb(new Error('boom')), [emp(1, '甲')], [], meta, onQuota)).rejects.toThrow('boom')
    expect(onQuota).not.toHaveBeenCalled()
  })
})
