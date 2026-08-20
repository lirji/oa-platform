#!/usr/bin/env node
/**
 * 首屏体积门禁。
 *
 * <p>量的是**未登录访客第一次访问实际下载的字节**（index.html 里被引用/预加载的那些），
 * 而不是 dist 下所有文件之和 —— 后者会把懒加载的路由块也算进去，那对首屏没有意义。
 *
 * <p>★ 为什么要有它：300 KB 是硬验收，而这个数字**只会往上漂**。
 * 加一个 antd 组件、少写一个 lazy，都会悄悄把它推过线；
 * 而没有断言的话，等到有人想起来量的时候，已经不知道是哪次改动引入的了。
 *
 * 用法：pnpm build && node scripts/check-size.mjs
 */
import { readFileSync, existsSync } from 'node:fs'
import { gzipSync } from 'node:zlib'
import { join } from 'node:path'

const DIST = 'dist'
const BUDGET_KB = Number(process.env.OA_SIZE_BUDGET_KB ?? 300)

const html = readFileSync(join(DIST, 'index.html'), 'utf8')
const files = [...new Set(html.match(/assets\/[A-Za-z0-9_.-]+\.(?:js|css)/g) ?? [])].sort()

if (files.length === 0) {
  console.error('没有在 index.html 里找到任何资源引用 —— 构建产物不对？')
  process.exit(1)
}

let total = 0
console.log('首屏资源（index.html 直接引用 / 预加载）：')
for (const f of files) {
  const p = join(DIST, f)
  if (!existsSync(p)) { console.error(`  缺失 ${f}`); process.exit(1) }
  const n = gzipSync(readFileSync(p)).length
  total += n
  console.log(`  ${f.padEnd(46)} ${(n / 1024).toFixed(1).padStart(7)} KB`)
}

const kb = total / 1024
const pct = ((kb / BUDGET_KB) * 100).toFixed(0)
console.log(`\n首屏合计 gzip：${kb.toFixed(1)} KB / 预算 ${BUDGET_KB} KB（${pct}%）`)

if (kb > BUDGET_KB) {
  console.error(`\n❌ 超出预算 ${(kb - BUDGET_KB).toFixed(1)} KB。`)
  console.error('   常见原因：某个页面忘了 lazy，把它引用的 antd 组件拖进了首屏块。')
  console.error('   排查：pnpm build 后看哪个 chunk 变大了，或临时给 rollup 加 visualizer。')
  process.exit(1)
}
console.log('✅ 在预算内')
