import { z } from 'zod'

/** MyBatis mapUnderscoreToCamelCase 会把列名变成 camelCase；这里两种都收。 */
function pick(row: Record<string, unknown>, ...keys: string[]): unknown {
  for (const k of keys) {
    if (row[k] !== undefined && row[k] !== null) return row[k]
  }
  return undefined
}

function asString(row: Record<string, unknown>, ...keys: string[]): string {
  const v = pick(row, ...keys)
  if (typeof v === 'string' && v) return v
  if (typeof v === 'number') return String(v)
  throw new Error(`缺少字段 ${keys[0]}`)
}

function asNumber(row: Record<string, unknown>, ...keys: string[]): number {
  const v = pick(row, ...keys)
  const n = typeof v === 'number' ? v : typeof v === 'string' ? Number(v) : NaN
  if (!Number.isFinite(n)) throw new Error(`字段 ${keys[0]} 不是数字`)
  return n
}

function asNumberOrNull(row: Record<string, unknown>, ...keys: string[]): number | null {
  const v = pick(row, ...keys)
  if (v == null || v === '') return null
  const n = typeof v === 'number' ? v : typeof v === 'string' ? Number(v) : NaN
  return Number.isFinite(n) ? n : null
}

export interface ApprovalRow {
  bizType: string
  total: number
  finished: number
  running: number
  rejected: number
  avgHours: number | null
  maxHours: number | null
}

export interface HeadcountRow {
  orgId: number
  orgName: string
  orgPath: string
  headcount: number
}

export function parseApproval(raw: unknown): ApprovalRow {
  const row = z.record(z.unknown()).parse(raw)
  return {
    bizType: asString(row, 'bizType', 'biz_type'),
    total: asNumber(row, 'total'),
    finished: asNumber(row, 'finished'),
    running: asNumber(row, 'running'),
    rejected: asNumber(row, 'rejected'),
    avgHours: asNumberOrNull(row, 'avgHours', 'avg_hours'),
    maxHours: asNumberOrNull(row, 'maxHours', 'max_hours'),
  }
}

export function parseHeadcount(raw: unknown): HeadcountRow {
  const row = z.record(z.unknown()).parse(raw)
  return {
    orgId: asNumber(row, 'orgId', 'org_id'),
    orgName: asString(row, 'orgName', 'org_name'),
    orgPath: asString(row, 'orgPath', 'org_path'),
    headcount: asNumber(row, 'headcount'),
  }
}
