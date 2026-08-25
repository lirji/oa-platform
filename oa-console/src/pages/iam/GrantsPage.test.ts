import { describe, expect, it } from 'vitest'
import { assignableScopes } from './GrantsPage'

describe('grant scope ceiling', () => {
  it('does not offer ALL above an ORG_AND_SUB role ceiling', () => {
    expect(assignableScopes('ORG_AND_SUB')).toContain('CUSTOM')
    expect(assignableScopes('ORG_AND_SUB')).not.toContain('ALL')
  })

  it('keeps SELF roles self-only', () => {
    expect(assignableScopes('SELF')).toEqual(['SELF', 'NONE'])
  })
})
