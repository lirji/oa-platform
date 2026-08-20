#!/usr/bin/env tsx
/**
 * 从**后端迁移 SQL** 生成 permCode 的 TS 常量（`src/shared/perm/codes.ts`）。
 *
 * ## 为什么真值源是迁移而不是运行中的接口
 *
 * 硬约束第 1 条：一个 `@RequiresPerm` 的 code **必须**同时出现在某个模块的迁移里，
 * 目录里没有的 code 判权时一律拒绝。所以迁移就是权限点目录的真值源，
 * 而且它在仓库里 —— 生成不需要起后端，CI 也跑得动。
 * （`GET /iam/permissions/catalog` 是**运行期**视图，多了 route/icon 这些会变的东西。）
 *
 * ## 刻意只生成三类信息
 *
 * 只输出**编译期用得上**的：code 集合、`require_elevation`、`status=DISABLED`。
 * **不输出** name / route / icon / sortOrder —— 那些前端在运行期从 `/me/permissions`
 * 拿，抄进代码就成了第二份菜单定义，两份迟早漂（V13 的注释已经讲过这件事）。
 *
 * 而这里生成的三类之所以敢落盘，是因为 `codes.test.ts` 每次都重新生成一遍并与
 * 提交进仓库的文件逐字节比对 —— 改了迁移不重跑生成，构建就红。
 *
 * 用法：
 *   pnpm gen:perm          写入 src/shared/perm/codes.ts
 *   pnpm gen:perm --check  只校验是否最新（不写文件，不一致则退出码 1）
 */
import { readdirSync, statSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { join, resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
// 显式 import 而不是用全局 `process`：tsconfig 的 types 里不加 "node"，
// src/ 下就不会意外拿到 node 的全局类型（这是个浏览器应用）。
import process from 'node:process'

const HERE = dirname(fileURLToPath(import.meta.url))
/** oa-platform 仓库根（oa-console 的上一级）。 */
export const REPO_ROOT = resolve(HERE, '../..')
export const OUT_FILE = resolve(HERE, '../src/shared/perm/codes.ts')

export interface PermEntry {
  code: string
  /** MENU | BUTTON | API | DATA | FIELD */
  type: string
  module: string | null
  requireElevation: boolean
  /** ACTIVE | DISABLED */
  status: string
  /** 四份 api-surface.golden 里是否真有 handler 用它 */
  hasHandler: boolean
}

// ───────────────────────────────────────────── SQL 扫描

/**
 * 去掉注释。**必须是引号感知的**：直接 `/--.*$/gm` 会把
 * `'目录中已定义但尚无接口实现'` 这类字符串里恰好出现的 `--` 当成注释起点，
 * 于是那条 UPDATE 的后半段（含 `WHERE code IN (...)`）被整段吃掉，
 * 三个 DISABLED 的 code 就会静默地变回 ACTIVE —— 生成的文件看起来完全正常。
 */
export function stripSqlComments(sql: string): string {
  let out = ''
  let i = 0
  let inStr = false
  while (i < sql.length) {
    const c = sql[i]
    if (inStr) {
      out += c
      // PG 里字符串内的单引号用 '' 转义
      if (c === "'") inStr = sql[i + 1] === "'" ? (out += sql[++i], true) : false
      i++
      continue
    }
    if (c === "'") { inStr = true; out += c; i++; continue }
    if (c === '-' && sql[i + 1] === '-') { while (i < sql.length && sql[i] !== '\n') i++; continue }
    if (c === '/' && sql[i + 1] === '*') { i += 2; while (i < sql.length && !(sql[i] === '*' && sql[i + 1] === '/')) i++; i += 2; continue }
    out += c
    i++
  }
  return out
}

/** 把 `'a', 'b', true, false` 这样一行拆成字段（引号感知，别用 split(',')）。 */
function splitTuple(body: string): string[] {
  const parts: string[] = []
  let cur = ''
  let inStr = false
  for (let i = 0; i < body.length; i++) {
    const c = body[i]
    if (inStr) {
      if (c === "'") {
        if (body[i + 1] === "'") { cur += "'"; i++ } else inStr = false
      } else cur += c
      continue
    }
    if (c === "'") { inStr = true; continue }
    if (c === ',') { parts.push(cur.trim()); cur = ''; continue }
    cur += c
  }
  parts.push(cur.trim())
  return parts
}

function walkSql(dir: string, acc: string[] = []): string[] {
  if (!existsSync(dir)) return acc
  for (const name of readdirSync(dir).sort()) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) walkSql(p, acc)
    else if (name.endsWith('.sql')) acc.push(p)
  }
  return acc
}

/** 所有模块的迁移目录（跳过 target/，那里是构建产物的副本）。 */
export function migrationFiles(root = REPO_ROOT): string[] {
  const files: string[] = []
  for (const mod of readdirSync(root).sort()) {
    const p = join(root, mod)
    if (!existsSync(p) || !statSync(p).isDirectory() || mod.startsWith('.')) continue
    walkSql(join(p, 'src/main/resources/db/migration'), files)
  }
  return files
}

// ───────────────────────────────────────────── 解析

export function parseCatalog(sqlFiles: string[]): Map<string, PermEntry> {
  const byCode = new Map<string, PermEntry>()

  for (const f of sqlFiles) {
    const sql = stripSqlComments(readFileSync(f, 'utf8'))

    // INSERT INTO oa_iam.permission (col, ...) VALUES (...), (...);
    const insertRe = /INSERT\s+INTO\s+oa_iam\.permission\s*\(([^)]*)\)\s*VALUES([\s\S]*?);/gi
    for (const m of sql.matchAll(insertRe)) {
      const cols = m[1].split(',').map((c) => c.trim().toLowerCase())
      const iCode = cols.indexOf('code')
      if (iCode < 0) continue
      for (const t of m[2].matchAll(/\(([^()]*)\)/g)) {
        const v = splitTuple(t[1])
        const pick = (col: string) => { const i = cols.indexOf(col); return i >= 0 ? v[i] : undefined }
        const code = v[iCode]
        if (!code?.startsWith('oa:')) continue
        byCode.set(code, {
          code,
          type: pick('type') ?? 'API',
          module: pick('module') ?? null,
          requireElevation: (pick('require_elevation') ?? 'false').toLowerCase() === 'true',
          status: (pick('status') ?? 'ACTIVE').toUpperCase(),
          hasHandler: false,
        })
      }
    }

    // UPDATE oa_iam.permission SET status = 'X' ... WHERE code = 'a' | WHERE code IN ('a','b')
    for (const m of sql.matchAll(/UPDATE\s+oa_iam\.permission\s+SET([\s\S]*?);/gi)) {
      const stmt = m[1]
      const st = /status\s*=\s*'([A-Z]+)'/i.exec(stmt)
      if (!st) continue
      const codes = [...stmt.matchAll(/'(oa:[^']+)'/g)].map((x) => x[1])
      for (const c of codes) {
        const e = byCode.get(c)
        if (e) e.status = st[1].toUpperCase()
      }
    }
  }
  return byCode
}

/** 四份 api-surface.golden 里出现过的 code —— 即"真有 handler 在用"。 */
export function parseGolden(root = REPO_ROOT): Set<string> {
  const set = new Set<string>()
  for (const mod of readdirSync(root).sort()) {
    const g = join(root, mod, 'src/test/resources/api-surface.golden')
    if (!existsSync(g)) continue
    for (const line of readFileSync(g, 'utf8').split('\n')) {
      const m = /\s(oa:[a-z0-9:-]+)\s*(\[[^\]]*\])?\s*$/.exec(line)
      if (m) set.add(m[1])
    }
  }
  return set
}

/** `oa:flow:todo:view` → `FLOW_TODO_VIEW`。 */
export function codeToKey(code: string): string {
  return code.replace(/^oa:/, '').replace(/[:-]/g, '_').toUpperCase()
}

// ───────────────────────────────────────────── 渲染

export function render(entries: PermEntry[]): string {
  const sorted = [...entries].sort((a, b) => (a.code < b.code ? -1 : a.code > b.code ? 1 : 0))

  const keys = new Map<string, string>()
  for (const e of sorted) {
    const k = codeToKey(e.code)
    const clash = keys.get(k)
    if (clash) throw new Error(`常量名冲突：${clash} 与 ${e.code} 都映射到 ${k}`)
    keys.set(k, e.code)
  }

  const pad = Math.max(...sorted.map((e) => codeToKey(e.code).length))
  const lines = sorted.map((e) => {
    const flags = [e.status === 'DISABLED' ? '无 handler，勿渲染入口' : '', e.requireElevation ? '需 JIT 提权' : '']
      .filter(Boolean).join(' · ')
    return `  ${(codeToKey(e.code) + ':').padEnd(pad + 1)} '${e.code}',${flags ? ` // ${flags}` : ''}`
  })

  const list = (f: (e: PermEntry) => boolean) =>
    sorted.filter(f).map((e) => `  '${e.code}',`).join('\n')

  return `/* eslint-disable */
/**
 * ⚠️ 本文件由 \`pnpm gen:perm\` 从后端迁移 SQL 生成，**不要手改**。
 * 改了迁移就重跑一次；忘了重跑的话 \`codes.test.ts\` 会让构建失败。
 *
 * 只含编译期用得上的三类信息（code / 是否需提权 / 是否 DISABLED）。
 * name、route、icon、sortOrder 一律运行期从 \`/me/permissions\` 取 ——
 * 抄进代码就成了第二份菜单定义。
 *
 * 共 ${sorted.length} 个权限点，其中 ${sorted.filter((e) => e.hasHandler).length} 个有 handler 在用。
 */

export const PERM = {
${lines.join('\n')}
} as const

/** 全部合法 permCode 的联合类型。写错一个字母就编译不过。 */
export type PermCode = (typeof PERM)[keyof typeof PERM]

/** 用于运行期校验与元测试（A10：页面里的字面量必须 ⊆ 这个集合）。 */
export const ALL_PERM_CODES: ReadonlySet<string> = new Set<string>(Object.values(PERM))

/**
 * 持有永久授权也必须先 JIT 提权才放行的 code。
 * 前端据此**提前**提示"这一步需要提权"，而不是等后端回 3002 才反应 ——
 * 但真正的边界永远是后端那次 3002，这里只是体验。
 */
export const ELEVATION_REQUIRED_CODES: ReadonlySet<string> = new Set<string>([
${list((e) => e.requireElevation)}
])

/**
 * 目录里已定义、但**没有任何 handler 消费**的 code（后端已标 DISABLED）。
 * 为它们渲染入口 = 用户点了 404，比没有这个按钮更糟。
 */
export const DISABLED_PERM_CODES: ReadonlySet<string> = new Set<string>([
${list((e) => e.status === 'DISABLED')}
])
`
}

// ───────────────────────────────────────────── 入口

export function generate(root = REPO_ROOT): string {
  const catalog = parseCatalog(migrationFiles(root))
  const golden = parseGolden(root)

  // ★ golden 里有、目录里没有 = 那个接口**永远判不过**（判权找不到 code 一律拒绝）。
  //   这是硬约束第 1 条的另一半，后端的覆盖率测试查不到它 —— 那边只查"注解在不在"。
  const orphanHandlers = [...golden].filter((c) => !catalog.has(c))
  if (orphanHandlers.length) {
    throw new Error(
      `以下 code 有 handler 在用，但任何迁移里都没有它们 —— 对应接口会永远返回 3001：\n` +
        orphanHandlers.map((c) => `  ${c}`).join('\n'),
    )
  }

  for (const [code, e] of catalog) e.hasHandler = golden.has(code)

  // 目录里 ACTIVE 却没有 handler 的：只提示不失败。
  // MENU / FIELD / DATA 本来就不对应接口（菜单是入口、字段是脱敏、数据是范围），不算孤儿。
  const NOT_ENDPOINT = new Set(['MENU', 'FIELD', 'DATA'])
  const noHandler = [...catalog.values()].filter((e) => !e.hasHandler && e.status === 'ACTIVE' && !NOT_ENDPOINT.has(e.type))
  if (noHandler.length) {
    console.warn(`⚠️  ${noHandler.length} 个 ACTIVE 权限点没有 handler 在用（可能是孤儿，考虑标 DISABLED）：`)
    for (const e of noHandler) console.warn(`     ${e.code}`)
  }

  return render([...catalog.values()])
}

const isMain = process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))
if (isMain) {
  const content = generate()
  const check = process.argv.includes('--check')
  const current = existsSync(OUT_FILE) ? readFileSync(OUT_FILE, 'utf8') : ''
  if (check) {
    if (current !== content) {
      console.error('❌ src/shared/perm/codes.ts 与迁移不一致 —— 请跑 `pnpm gen:perm`')
      process.exit(1)
    }
    console.log('✅ codes.ts 是最新的')
  } else {
    writeFileSync(OUT_FILE, content)
    const n = (content.match(/^ {2}[A-Z0-9_]+: +'/gm) ?? []).length
    console.log(`✅ 写入 ${OUT_FILE}（${n} 个权限点）`)
  }
}
