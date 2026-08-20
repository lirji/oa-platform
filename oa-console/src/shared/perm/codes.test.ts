import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { generate, OUT_FILE } from '../../../scripts/gen-perm'
import { ALL_PERM_CODES, DISABLED_PERM_CODES, PERM } from './codes'

/**
 * 验收 A10：permCode 无漂移。
 *
 * <p>这一页守的是这样一类事故：后端把某个接口的权限点从 `oa:org:view` 改成
 * `oa:org:admin`，编译过、单测过、冒烟也可能过（冒烟只覆盖主流程），
 * 而前端在**某个不常点的页面**上静默地把按钮藏起来或者 404。
 * 后端那边由 `api-surface.golden` 冻结，前端这边由这两条断言冻结。
 */

const SRC = resolve(__dirname, '../..')

/**
 * 去掉注释后再找字符串字面量。
 *
 * <p>★ 不能直接 grep `'oa:xxx'` —— `ProtectedRoute.tsx` 的注释里写着
 * `<PermRoute code="oa:menu:xxx">` 这样的**示意占位**，它不是引用，
 * 却会让断言失败。反过来，如果为了绕开它把匹配收窄成
 * `code="..."` 这一种写法，那 `guarded('oa:org:view', …)` 和
 * `sp.get('permCode') ?? 'oa:employee:view'` 这两处真引用就扫不到了 ——
 * 一个扫不到东西的检查会永远通过。
 */
function stringLiterals(code: string): string[] {
  const out: string[] = []
  let i = 0
  while (i < code.length) {
    const c = code[i]
    if (c === '/' && code[i + 1] === '/') { while (i < code.length && code[i] !== '\n') i++; continue }
    if (c === '/' && code[i + 1] === '*') { i += 2; while (i < code.length && !(code[i] === '*' && code[i + 1] === '/')) i++; i += 2; continue }
    if (c === "'" || c === '"' || c === '`') {
      const quote = c
      let buf = ''
      i++
      while (i < code.length && code[i] !== quote) {
        if (code[i] === '\\') { buf += code[i + 1] ?? ''; i += 2; continue }
        buf += code[i++]
      }
      i++
      out.push(buf)
      continue
    }
    i++
  }
  return out
}

function sourceFiles(dir: string, acc: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) sourceFiles(p, acc)
    else if (/\.tsx?$/.test(name) && !/codes\.(ts|test\.ts)$/.test(name)) acc.push(p)
  }
  return acc
}

describe('permCode 常量与源码的一致性（验收 A10）', () => {
  const files = sourceFiles(SRC)
  const used = new Map<string, string[]>()
  for (const f of files) {
    for (const lit of stringLiterals(readFileSync(f, 'utf8'))) {
      if (/^oa:[a-z0-9:-]+$/.test(lit)) {
        used.set(lit, [...(used.get(lit) ?? []), f.slice(SRC.length + 1)])
      }
    }
  }

  it('确实扫到了源码与引用（否则这条检查永远通过）', () => {
    // 扫不到东西的检查比没有检查更糟：它会给出"已经验过了"的假象。
    expect(files.length).toBeGreaterThan(20)
    expect(used.size).toBeGreaterThan(5)
  })

  it('源码里出现的每个 permCode 都在生成的目录里', () => {
    const unknown = [...used].filter(([c]) => !ALL_PERM_CODES.has(c))
    expect(
      unknown.map(([c, where]) => `${c} ← ${where.join(', ')}`),
      '这些 code 在后端权限点目录里不存在 —— 判权时一律拒绝，按钮会永远不出现',
    ).toEqual([])
  })

  it('UI 源码不引用 DISABLED 的 code（目录里有、但没有任何 handler）', () => {
    // ★ 只管 UI 源码：测试文件拿 DISABLED 的 code 当夹具是正当的
    //   （`errors.test.ts` 用 `oa:employee:export` 造 3002，它恰好是需提权的那类），
    //   把夹具也算进来会逼着测试去挑一个"正在被用"的 code —— 那才是真会漂的写法。
    const dead = [...used]
      .map(([c, where]) => [c, where.filter((w) => !/\.test\.tsx?$/.test(w))] as const)
      .filter(([c, where]) => DISABLED_PERM_CODES.has(c) && where.length > 0)
    expect(
      dead.map(([c, where]) => `${c} ← ${where.join(', ')}`),
      '为没有 handler 的 code 渲染入口 = 用户点了 404，比没有这个按钮更糟',
    ).toEqual([])
  })
})

describe('codes.ts 与后端迁移同步', () => {
  it('重新生成一遍与仓库里的文件逐字节相同', () => {
    // 改了迁移却忘了 `pnpm gen:perm` 时，这里红。
    // 没有这条的话，生成物会安静地停留在上一个版本 —— 而它看起来完全正常。
    expect(readFileSync(OUT_FILE, 'utf8')).toBe(generate())
  })

  it('PERM 的键与值一一对应且无空目录', () => {
    expect(Object.keys(PERM).length).toBe(ALL_PERM_CODES.size)
    expect(ALL_PERM_CODES.size).toBeGreaterThan(50)
  })
})
