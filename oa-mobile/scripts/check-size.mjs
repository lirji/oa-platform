#!/usr/bin/env node
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { gzipSync } from 'node:zlib'

const budget = Number(process.env.OA_MOBILE_SIZE_BUDGET_KB ?? 220)
const html = readFileSync(join('dist', 'index.html'), 'utf8')
const files = [...new Set(html.match(/assets\/[A-Za-z0-9_.-]+\.(?:js|css)/g) ?? [])].sort()
if (!files.length) { console.error('构建产物没有首屏资源'); process.exit(1) }
let bytes = 0
for (const file of files) {
  const target = join('dist', file)
  if (!existsSync(target)) { console.error(`缺少 ${file}`); process.exit(1) }
  const size = gzipSync(readFileSync(target)).length
  bytes += size
  console.log(`${file.padEnd(44)} ${(size / 1024).toFixed(1).padStart(7)} KB`)
}
const kb = bytes / 1024
console.log(`\n移动端首屏 gzip：${kb.toFixed(1)} KB / 预算 ${budget} KB`)
if (kb > budget) { console.error(`超出预算 ${(kb - budget).toFixed(1)} KB`); process.exit(1) }
console.log('✅ 在预算内')
