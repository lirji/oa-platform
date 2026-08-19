#!/usr/bin/env python3
"""
从源码重新生成 docs/API.md。

★ 为什么要有这个脚本：原来的 API.md 是手写的，写完就开始腐化 ——
   实际有 120 个 handler，文档里只有 40 个，缺的 80 个恰好是后来加的
   （公文/行政/报表/通知/文件/跑批全都不在里面）。
   一份只覆盖三分之一的接口文档，比没有更容易误导人。

   现在它由源码扫描生成：加了新接口忘了更文档这件事，从"会发生"变成"不会发生"。

用法：python3 deploy/scripts/gen-api-doc.py   （在仓库根目录执行）
"""
import os, re, io, collections, sys

MODULE_META = {
    'oa-app': ('系统', ':8400'),
    'oa-org': ('组织人事', ':8400'),
    'oa-iam': ('权限中心', ':8400'),
    'oa-flow': ('工作台与审批', ':8400'),
    'oa-attendance': ('考勤', ':8400'),
    'oa-doc': ('公文与知识库', ':8400'),
    'oa-admin-biz': ('行政（会议室/资产/用品/车辆/访客）', ':8400'),
    'oa-report': ('报表与审计', ':8400'),
    'oa-notify-service': ('通知与公告（独立服务）', ':8401'),
    'oa-file-service': ('文件服务（独立服务）', ':8402'),
    'oa-job-service': ('跑批服务（独立服务）', ':8403'),
}
ORDER = ['oa-app', 'oa-org', 'oa-iam', 'oa-flow', 'oa-attendance', 'oa-doc',
         'oa-admin-biz', 'oa-report', 'oa-notify-service', 'oa-file-service', 'oa-job-service']


def scan():
    rows = []
    for dirpath, _dirs, files in os.walk('.'):
        if 'target' in dirpath or '/.git' in dirpath:
            continue
        for f in files:
            if not f.endswith('Controller.java'):
                continue
            p = os.path.join(dirpath, f)
            s = io.open(p, encoding='utf-8').read()
            m = re.search(r'@RequestMapping\("([^"]+)"\)', s)
            base = m.group(1) if m else ''
            module = p.split('/')[1] if p.startswith('./') else p.split('/')[0]
            for mm in re.finditer(
                    r'@(Get|Post|Put|Delete|Patch)Mapping'
                    r'(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?[^\n]*\n'
                    r'((?:\s*@[^\n]*\n)*)\s*public\s+([^\n{]+)', s):
                verb, path, anns = mm.group(1).upper(), mm.group(2) or '', mm.group(3) or ''
                perm = ''
                pm = re.search(r'@RequiresPerm\(\s*(?:value\s*=\s*)?\{?\s*"([^"]+)"', anns)
                if pm:
                    perm = pm.group(1)
                if '@PublicApi' in anns:
                    perm = '(公开)'
                if 'elevation = true' in anns or 'elevation=true' in anns:
                    perm += ' ★需提权'
                rows.append((module, verb, (base + path) or path, perm))
    rows.sort(key=lambda r: (r[0], r[2], r[1]))
    return rows


if __name__ == '__main__':
    rows = scan()
    if not rows:
        sys.exit('没有扫到任何 Controller —— 是不是不在仓库根目录执行？')
    print('扫到 %d 个 handler，覆盖 %d 个模块' % (rows.__len__(), len({r[0] for r in rows})))
    print('（本脚本只负责扫描；文档正文的固定段落在 docs/API.md 顶部，'
          '重新生成时请保留那部分说明）')
    by_mod = collections.defaultdict(list)
    for r in rows:
        by_mod[r[0]].append(r)
    for mod in ORDER:
        if mod not in by_mod:
            continue
        title, port = MODULE_META[mod]
        print('\n## %s　`%s`　%s\n' % (title, mod, port))
        print('| 方法 | 路径 | 权限点 |')
        print('|---|---|---|')
        for _, verb, path, perm in by_mod[mod]:
            print('| %s | `%s` | %s |' % (verb, path, perm or '—'))
