import { describe, expect, it } from 'vitest'
import {
  attachChildren, canDrop, collectIds, filterTree, inOrgSubtree, indexTree,
  suggestedChildType, type TreePathNode,
} from './orgTree'

/**
 * 计划 §10 单测 5：成环判定表驱动。
 *
 * 树形（path 恒为 `/a/b/c/`，首尾都有斜杠 —— 后端 `OrgUnitService` 的不变量）：
 *
 *   1 集团            /1/
 *   ├── 2 研发        /1/2/
 *   │   └── 4 后端    /1/2/4/
 *   ├── 23 市场       /1/23/     ← id 是 2 的字符串前缀，专门放的
 *   └── 3 财务        /1/3/
 */
const TREE: TreePathNode[] = [
  {
    id: 1, path: '/1/', children: [
      { id: 2, path: '/1/2/', children: [{ id: 4, path: '/1/2/4/' }] },
      { id: 23, path: '/1/23/' },
      { id: 3, path: '/1/3/' },
    ],
  },
]
const IDX = indexTree(TREE)

describe('indexTree', () => {
  it('把嵌套树摊平（含深层节点）', () => {
    expect([...IDX.keys()].sort((a, b) => a - b)).toEqual([1, 2, 3, 4, 23])
    expect(IDX.get(4)?.path).toBe('/1/2/4/')
  })

  it('空树不炸', () => {
    expect(indexTree([]).size).toBe(0)
  })
})

describe('canDrop —— 成环预判', () => {
  const cases: Array<[string, number, number, boolean]> = [
    ['同级之间可以拖',                 2, 3, true],
    ['可以拖到父节点',                 4, 1, true],
    ['不能拖到自己',                   2, 2, false],
    ['不能拖到直接子节点',             2, 4, false],
    ['不能拖到根（根是所有人的祖先）', 1, 4, false],
    ['★ 可以拖进 id 是自己前缀的兄弟', 2, 23, true],
    ['★ 反向也可以',                   23, 2, true],
    ['拖动节点不存在 → 禁止',          99, 2, false],
    ['目标节点不存在 → 禁止',          2, 99, false],
  ]
  for (const [name, drag, drop, want] of cases) {
    it(`${name}（${drag} → ${drop} = ${want}）`, () => {
      expect(canDrop(drag, drop, IDX)).toBe(want)
    })
  }

  it('★ 尾斜杠是这个判定成立的前提', () => {
    // 后端保证 path 恒为 /a/b/c/。若哪天少了尾斜杠，"/1/23" 会被判成 "/1/2" 的后代 ——
    // 表现是"市场部不能拖进研发部"，而两者毫无关系。这条钉住那个前提。
    const broken = indexTree<TreePathNode>([
      { id: 2, path: '/1/2' },
      { id: 23, path: '/1/23' },
    ])
    expect(canDrop(2, 23, broken)).toBe(false) // ← 少了尾斜杠就会误判成禁止
    expect(canDrop(2, 23, IDX)).toBe(true)     // ← 有尾斜杠才是对的
  })
})

describe('attachChildren', () => {
  it('只替换目标节点的 children，兄弟不动', () => {
    const next = attachChildren(TREE, 23, [
      { id: 230, path: '/1/23/230/' },
    ])
    expect(next[0].children?.find((n) => n.id === 2)?.children).toEqual([{ id: 4, path: '/1/2/4/' }])
    expect(next[0].children?.find((n) => n.id === 23)?.children).toEqual([{ id: 230, path: '/1/23/230/' }])
  })

  it('空 children 也写回去（懒加载证明这是叶子）', () => {
    const next = attachChildren([{ id: 1, path: '/1/', children: [] }], 1, [])
    expect(next[0].children).toEqual([])
  })
})

describe('filterTree', () => {
  interface Named extends TreePathNode {
    name: string
    code: string
    children?: Named[]
  }
  const named: Named[] = [
    {
      id: 1, path: '/1/', name: '集团', code: 'G', children: [
        { id: 2, path: '/1/2/', name: '研发', code: 'RD', children: [{ id: 4, path: '/1/2/4/', name: '后端', code: 'BE' }] },
        { id: 23, path: '/1/23/', name: '市场', code: 'MKT' },
      ],
    },
  ]

  it('空关键字原样返回', () => {
    expect(filterTree(named, '  ')).toBe(named)
  })

  it('命中叶子时保留祖先', () => {
    const hit = filterTree(named, '后端')
    expect(collectIds(hit)).toEqual([1, 2, 4])
  })

  it('按编码也能搜', () => {
    expect(collectIds(filterTree(named, 'mkt'))).toEqual([1, 23])
  })
})

describe('inOrgSubtree', () => {
  const org = { id: 2, path: '/1/2/' }

  it('本级命中', () => {
    expect(inOrgSubtree({ orgId: 2, orgPath: '/1/2/' }, org)).toBe(true)
  })

  it('下级 path 前缀命中；id 是前缀的兄弟不命中', () => {
    expect(inOrgSubtree({ orgId: 4, orgPath: '/1/2/4/' }, org)).toBe(true)
    expect(inOrgSubtree({ orgId: 23, orgPath: '/1/23/' }, org)).toBe(false)
  })

  it('仅本级时下级不算', () => {
    expect(inOrgSubtree({ orgId: 4, orgPath: '/1/2/4/' }, org, true)).toBe(false)
  })
})

describe('suggestedChildType', () => {
  it('按常见标签给默认下级类型', () => {
    expect(suggestedChildType('GROUP')).toBe('COMPANY')
    expect(suggestedChildType('DEPT')).toBe('TEAM')
    expect(suggestedChildType('TEAM')).toBe('SQUAD')
    expect(suggestedChildType('VIRTUAL')).toBe('TEAM')
  })
})
