#!/usr/bin/env node
/**
 * 固定 oa-app 的 OpenAPI 快照并生成 TypeScript 字段契约。
 *
 * `gen:api:fetch` 只对 localhost（或显式 `OA_OPENAPI_URL`）取快照；CI 使用仓库里的
 * 快照执行 `gen:api:check`，不需要假装起一套数据库。路径/权限归属仍由后端 golden
 * 测试守，字段形状由这里守，两者职责不重叠。
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import process from 'node:process'
import openapiTS, { astToString } from 'openapi-typescript'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '..')
const snapshot = resolve(root, 'openapi/oa-app.json')
const output = resolve(root, 'src/shared/types/openapi.d.ts')
const args = new Set(process.argv.slice(2))

if (args.has('--fetch')) {
  const source = process.env.OA_OPENAPI_URL ?? 'http://127.0.0.1:8400/v3/api-docs'
  const url = new URL(source)
  if (!['127.0.0.1', 'localhost', '::1'].includes(url.hostname) && !process.env.OA_OPENAPI_URL) {
    throw new Error('默认只允许从 localhost 获取 OpenAPI；其它环境必须显式设置 OA_OPENAPI_URL')
  }
  const response = await fetch(url, { signal: AbortSignal.timeout(15_000) })
  if (!response.ok) throw new Error(`OpenAPI 获取失败：HTTP ${response.status}`)
  const document = await response.json()
  if (!document?.openapi || !document?.paths || Object.keys(document.paths).length < 50) {
    throw new Error('OpenAPI 文档不完整，拒绝覆盖快照')
  }
  await mkdir(dirname(snapshot), { recursive: true })
  await writeFile(snapshot, `${JSON.stringify(document, null, 2)}\n`, 'utf8')
  console.log(`OpenAPI snapshot: ${Object.keys(document.paths).length} paths → ${snapshot}`)
}

const ast = await openapiTS(pathToFileURL(snapshot), {
  alphabetize: true,
  immutable: true,
  exportType: true,
})
const generated = astToString(ast)

if (args.has('--check')) {
  const current = await readFile(output, 'utf8').catch(() => '')
  if (current !== generated) {
    console.error('OpenAPI TypeScript 契约已漂移；请运行 pnpm gen:api')
    process.exitCode = 1
  } else {
    console.log('OpenAPI TypeScript 契约是最新的')
  }
} else {
  await mkdir(dirname(output), { recursive: true })
  await writeFile(output, generated, 'utf8')
  console.log(`OpenAPI types → ${output}`)
}
