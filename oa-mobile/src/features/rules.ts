import type { Announcement, Todo } from '../api/types'

export const canHandleTodo = (todo: Pick<Todo, 'taskId' | 'state'>, perms: ReadonlySet<string>) =>
  Boolean(todo.taskId) && todo.state === 'PENDING' && perms.has('oa:flow:todo:handle')
export const announcementNeedsRead = (item: Pick<Announcement, 'readByMe' | 'status'>) =>
  item.status === 'PUBLISHED' && item.readByMe !== true
export const directoryScopeLabel = (scope: string) => ({ ALL: '全部通讯录', SELF: '仅本人', ORG: '仅本部门', ORG_AND_SUB: '本部门及下级' }[scope] ?? '按授权范围')
export function punchParams(type: 'IN' | 'OUT', coords?: { latitude: number; longitude: number }) {
  return { type, source: 'MOBILE', ...(coords ? { lat: coords.latitude, lng: coords.longitude } : {}) }
}
