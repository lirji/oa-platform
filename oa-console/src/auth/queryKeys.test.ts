import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * 计划 §10 单测 8：**每个 per-viewer 的 queryKey 都必须带 `permVersion`**。
 *
 * <p>为什么这件事需要一条元测试守着：列表数据是后端按调用者的 `@DataScope`
 * 过滤出来的，所以缓存里那份**归属于某一个权限版本**。权限变了而 key 没变，
 * react-query 会拿旧数据继续渲染 —— 按钮按新权限消失了，行还在。
 * 这正好落在"数据权限不足不报错、只是少给行"（静默失败清单第 16 条）的阴影里：
 * 多给行同样不会报错。
 *
 * <p>它守的不是已经写对的那些，而是**下一个新增的查询**。
 */
const SRC = resolve(__dirname, '..')

/** 不带 permVersion 是正确的那些 key，每条都要写清为什么。 */
const EXEMPT: Record<string, string> = {
  PERM_KEY: '它就是权限本身 —— 把版本放进自己的 key 是循环',
  ELEVATION_KEY: '同上，提权清单与权限同源',
  "'perm-catalog'": '全局权限点目录，与看的人无关',
  "'iam-roles'": '全局角色列表（GET /iam/roles），与看的人无关',
}

function tsFiles(dir: string, acc: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) tsFiles(p, acc)
    else if (/\.tsx?$/.test(name) && !/\.test\.tsx?$/.test(name)) acc.push(p)
  }
  return acc
}

interface Found { file: string; line: number; key: string; first: string }

function collectQueryKeys(): Found[] {
  const out: Found[] = []
  for (const f of tsFiles(SRC)) {
    const lines = readFileSync(f, 'utf8').split('\n')
    lines.forEach((line, i) => {
      // invalidateQueries 里的 key 是**前缀**，故意不带版本（带了就只失效某一个版本）
      if (line.includes('invalidateQueries')) return
      const m = /queryKey:\s*\[([^\]]*)\]/.exec(line)
      if (!m) return
      const parts = m[1].split(',').map((x) => x.trim()).filter(Boolean)
      out.push({ file: f.slice(SRC.length + 1), line: i + 1, key: m[1].trim(), first: parts[0] ?? '' })
    })
  }
  return out
}

describe('queryKey 完整性（计划 §10 单测 8）', () => {
  const found = collectQueryKeys()

  it('确实扫到了查询定义（扫不到东西的检查会永远通过）', () => {
    expect(found.length).toBeGreaterThanOrEqual(12)
  })

  it('per-viewer 的 key 都带 permVersion', () => {
    const missing = found
      .filter((f) => !(f.first in EXEMPT))
      .filter((f) => !/permVersion/.test(f.key))
      .map((f) => `${f.file}:${f.line}  queryKey: [${f.key}]`)
    expect(
      missing,
      '这些查询的结果按调用者的数据范围过滤，权限一变缓存就不再属于他 ——\n' +
        '要么把 permVersion 加进 key，要么在 queryKeys.test.ts 的 EXEMPT 里写清为什么不需要',
    ).toEqual([])
  })

  it('豁免清单里的每一项都还真实存在（清单本身不许烂掉）', () => {
    for (const k of Object.keys(EXEMPT)) {
      expect(found.some((f) => f.first === k), `EXEMPT 里的 ${k} 已经没有对应查询了，删掉它`).toBe(true)
    }
  })
})
