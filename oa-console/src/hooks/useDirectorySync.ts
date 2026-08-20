import { useCallback, useEffect, useRef, useState } from 'react'
import { openDB, type IDBPDatabase } from 'idb'
import { apiClient } from '@oa/shared/api/client'
import { errText } from '@oa/shared/api/errors'
import { usePermStore } from '../store/permStore'

/**
 * 落盘的通讯录条目。
 *
 * <p>★ **手机号与邮箱刻意不在这里** —— 它们是敏感字段（后端 `@Sensitive` 脱敏），
 * 持久化到磁盘与"token 只存 sessionStorage、关标签页即清"的纪律直接矛盾。
 * 缓存的价值（秒开 + 本地搜索）100% 保留，风险降到接近零；详情实时拉。
 */
export interface DirEntry {
  employeeId: number
  userId: string
  empNo: string
  name: string
  orgName: string | null
  positionName: string | null
}

export interface Meta {
  /** 上次同步到的水位线。 */
  since: number
  /** ★ 缓存归属：谁的、在哪个权限版本下看到的。 */
  ownerId: string
  permVersion: number
  syncedAt: number
}

const DB_NAME = 'oa-directory'
const DB_VERSION = 1
const STORE = 'entries'
const META = 'meta'
const PAGE = 500   // 后端单次上限

async function open(): Promise<IDBPDatabase> {
  return openDB(DB_NAME, DB_VERSION, {
    upgrade(db) {
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE, { keyPath: 'employeeId' })
      if (!db.objectStoreNames.contains(META)) db.createObjectStore(META)
    },
  })
}

/**
 * 通讯录本地缓存 + 增量同步。
 *
 * <p><b>三条硬约束</b>（都来自"通讯录是 per-viewer 的"这个事实）：
 * <ol>
 *   <li>缓存按 <b>(userId, permVersion)</b> 归属。权限版本变了就整体作废 ——
 *       否则<b>被降权的人还能从本地缓存看到全公司名单</b>，
 *       一个完全发生在客户端、后端日志上毫无痕迹的越权。</li>
 *   <li>敏感字段不落盘（见 {@link DirEntry}）。</li>
 *   <li>必须处理 <b>墓碑</b>：离职是"行从视图里消失"而不是"行变化"，
 *       只收变更行的话本地会永远躺着已离职的人。</li>
 * </ol>
 */
export function useDirectorySync() {
  const userId = usePermStore((s) => s.userId)
  const permVersion = usePermStore((s) => s.version)
  const loaded = usePermStore((s) => s.loaded)

  const [entries, setEntries] = useState<DirEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [fatal, setFatal] = useState<string | null>(null)
  const [degraded, setDegraded] = useState(false)
  const [lastSyncedAt, setLastSyncedAt] = useState<number | null>(null)
  const running = useRef(false)

  const run = useCallback(async (forceFull = false) => {
    if (!userId || running.current) return
    running.current = true
    setError(null)
    // ★ 连接必须在 finally 里关。不关的话每次同步（每次权限版本变化、每次手动
    //   resync）都会多留一个 IDBDatabase 连接：既是泄漏，又会让将来的
    //   `DB_VERSION` 升级被 blocked —— 升级事务根本不会触发，
    //   表现是"新版 schema 的代码跑在旧 schema 的库上"。
    let db: IDBPDatabase | null = null
    try {
      try {
        db = await open()
      } catch {
        // 隐私模式 / 配额为 0：降级为纯网络，不再尝试写库
        setDegraded(true)
      }

      let meta = db ? ((await db.get(META, 'meta')) as Meta | undefined) : undefined
      // ★ 归属校验：换了人、或权限版本变了 → 缓存整体作废
      const stale = !meta || meta.ownerId !== userId || meta.permVersion !== permVersion
      if (forceFull || stale) {
        if (db) { await db.clear(STORE); }
        meta = undefined
      }

      let cached: DirEntry[] = db ? ((await db.getAll(STORE)) as DirEntry[]) : []
      if (cached.length) { setEntries(cached); setLoading(false) }

      if (!meta) {
        // 全量：游标分页拉。先渲染第一页，后台继续补齐 —— 万人一次拉完首屏要等好几秒。
        let cursor: number | undefined
        const all: DirEntry[] = []
        for (;;) {
          const url = `/api/v1/org/directory/page?size=${PAGE}${cursor ? `&cursor=${cursor}` : ''}`
          const { data } = await apiClient.get(url)
          const items = (data.data.items ?? []) as (DirEntry & { syncSeq: number })[]
          all.push(...items.map(strip))
          if (all.length && !cached.length) setEntries([...all])
          if (!data.data.hasMore || !items.length) {
            meta = {
              since: Number(data.data.watermark) || 0,
              ownerId: userId, permVersion, syncedAt: Date.now(),
            }
            break
          }
          cursor = Number(data.data.nextCursor)
        }
        if (db) await persist(db, all, [], meta!, setDegraded)
        setEntries(all)
      } else {
        // 增量：循环拉到 hasMore=false。
        // ★ 中途失败【不推进 since】—— 推进了会永久跳过中间那段变更。
        let since = meta.since
        const changed: DirEntry[] = []
        const deleted: number[] = []
        for (;;) {
          const { data } = await apiClient.get(`/api/v1/org/directory/delta?since=${since}&size=${PAGE}`)
          const d = data.data
          if (d.fullResync) { running.current = false; return run(true) }
          changed.push(...((d.changes ?? []) as (DirEntry & { syncSeq: number })[]).map(strip))
          deleted.push(...((d.deletions ?? []) as number[]))
          since = Number(d.nextSince) || since
          if (!d.hasMore) { since = Number(d.watermark) || since; break }
        }
        const next = merge(cached, changed, deleted)
        const newMeta: Meta = { since, ownerId: userId, permVersion, syncedAt: Date.now() }
        if (db) await persist(db, changed, deleted, newMeta, setDegraded)
        setEntries(next)
      }
      setLastSyncedAt(Date.now())
      setFatal(null)
    } catch (e) {
      const msg = errText(e)
      // 有缓存就只是"同步失败"，没缓存才是致命
      setError(msg)
      setEntries((prev) => { if (!prev.length) setFatal(msg); return prev })
    } finally {
      db?.close()
      setLoading(false)
      running.current = false
    }
  }, [userId, permVersion])

  useEffect(() => { if (loaded && userId) void run() }, [loaded, userId, permVersion, run])

  return {
    entries, loading, error, fatal, degraded, lastSyncedAt,
    /** 距上次同步是否已久（> 10 分钟）。UI 据此把角标变黄。 */
    stale: lastSyncedAt != null && Date.now() - lastSyncedAt > 10 * 60_000,
    resync: () => void run(true),
  }
}

const strip = (e: DirEntry & { syncSeq?: number }): DirEntry => ({
  employeeId: e.employeeId, userId: e.userId, empNo: e.empNo,
  name: e.name, orgName: e.orgName, positionName: e.positionName,
})

/**
 * 增量合并。导出是为了能表驱动地测 —— 墓碑处理写错的表现是
 * "本地一直躺着已离职的人"，而那在界面上看起来完全正常。
 */
export function merge(base: DirEntry[], changed: DirEntry[], deleted: number[]): DirEntry[] {
  const m = new Map(base.map((e) => [e.employeeId, e]))
  changed.forEach((e) => m.set(e.employeeId, e))
  deleted.forEach((id) => m.delete(id))
  return [...m.values()]
}

/**
 * 落盘一批变更。导出是为了能直接测配额超限那条分支 ——
 * 它只在磁盘真的满了时才走到，而那正是最不该在用户机器上第一次运行的代码。
 */
export async function persist(
  db: IDBPDatabase, changed: DirEntry[], deleted: number[], meta: Meta,
  onQuotaExceeded: (v: boolean) => void,
) {
  try {
    const tx = db.transaction([STORE, META], 'readwrite')
    await Promise.all([
      ...changed.map((e) => tx.objectStore(STORE).put(e)),
      ...deleted.map((id) => tx.objectStore(STORE).delete(id)),
      tx.objectStore(META).put(meta, 'meta'),
    ])
    await tx.done
  } catch (e) {
    // QuotaExceededError：降级为纯网络，**不再重试写库**（重试只会一次次失败）
    if ((e as DOMException)?.name === 'QuotaExceededError') onQuotaExceeded(true)
    else throw e
  }
}
