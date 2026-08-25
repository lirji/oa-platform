import { describe, expect, it } from 'vitest'
import { canDeleteRole, groupPermissions } from './RoleManagementPanel'

describe('RoleManagementPanel helpers', () => {
  it('only permits deleting unused custom roles', () => {
    expect(canDeleteRole({ builtin: false, grantCount: 0, status: 'ACTIVE' })).toBe(true)
    expect(canDeleteRole({ builtin: true, grantCount: 0, status: 'ACTIVE' })).toBe(false)
    expect(canDeleteRole({ builtin: false, grantCount: 1, status: 'DISABLED' })).toBe(false)
    expect(canDeleteRole({ builtin: false, grantCount: 0, status: 'DELETED' })).toBe(false)
  })

  it('groups permission catalog by module', () => {
    const groups = groupPermissions([
      { id: 1, code: 'oa:iam:view', name: '查看', type: 'API', module: 'iam', enabled: true },
      { id: 2, code: 'oa:org:view', name: '组织', type: 'API', module: 'org', enabled: true },
      { id: 3, code: 'oa:iam:admin', name: '管理', type: 'API', module: 'iam', enabled: true },
    ])
    expect(groups.iam.map((item) => item.id)).toEqual([1, 3])
    expect(groups.org.map((item) => item.id)).toEqual([2])
  })
})
