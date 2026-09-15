import { describe, expect, it } from 'vitest'
import { parseApproval, parseHeadcount } from './reportView'

describe('parseApproval', () => {
  it('收 camelCase（MyBatis 默认）', () => {
    expect(parseApproval({
      bizType: 'LEAVE', total: 10, finished: 4, running: 6, rejected: 1, avgHours: 12.5, maxHours: 40,
    })).toEqual({
      bizType: 'LEAVE', total: 10, finished: 4, running: 6, rejected: 1, avgHours: 12.5, maxHours: 40,
    })
  })

  it('收 snake_case，空耗时是 null 不是 0', () => {
    expect(parseApproval({
      biz_type: 'LEAVE', total: 2, finished: 0, running: 2, rejected: 0, avg_hours: null, max_hours: null,
    }).avgHours).toBeNull()
  })
})

describe('parseHeadcount', () => {
  it('组织 id 无论 camel / snake 都能读', () => {
    expect(parseHeadcount({ org_id: 3, org_name: '产品部', org_path: '/1/3/', headcount: 8 }).orgId).toBe(3)
    expect(parseHeadcount({ orgId: 3, orgName: '产品部', orgPath: '/1/3/', headcount: 8 }).orgName).toBe('产品部')
  })
})
