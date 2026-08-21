import { describe, expect, it } from 'vitest'
import { announcementNeedsRead, canHandleTodo, directoryScopeLabel, punchParams } from './rules'

describe('mobile feature rules', () => {
  it('only permits pending todo handling with the required permission', () => {
    expect(canHandleTodo({ taskId: 't-1', state: 'PENDING' }, new Set(['oa:flow:todo:handle']))).toBe(true)
    expect(canHandleTodo({ taskId: 't-1', state: 'DONE' }, new Set(['oa:flow:todo:handle']))).toBe(false)
    expect(canHandleTodo({ taskId: 't-1', state: 'PENDING' }, new Set())).toBe(false)
  })
  it('distinguishes unread published announcements', () => {
    expect(announcementNeedsRead({ status: 'PUBLISHED', readByMe: false })).toBe(true)
    expect(announcementNeedsRead({ status: 'REVOKED', readByMe: false })).toBe(false)
  })
  it('maps data scope to employee-facing language', () => {
    expect(directoryScopeLabel('ORG_AND_SUB')).toBe('本部门及下级')
    expect(directoryScopeLabel('unknown')).toBe('按授权范围')
  })
  it('builds a mobile punch with optional coordinates', () => {
    expect(punchParams('IN')).toEqual({ type: 'IN', source: 'MOBILE' })
    expect(punchParams('OUT', { latitude: 25.04, longitude: 121.56 })).toEqual({ type: 'OUT', source: 'MOBILE', lat: 25.04, lng: 121.56 })
  })
})
