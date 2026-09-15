/* eslint-disable */
/**
 * ⚠️ 本文件由 `pnpm gen:perm` 从后端迁移 SQL 生成，**不要手改**。
 * 改了迁移就重跑一次；忘了重跑的话 `codes.test.ts` 会让构建失败。
 *
 * 只含编译期用得上的三类信息（code / 是否需提权 / 是否 DISABLED）。
 * name、route、icon、sortOrder 一律运行期从 `/me/permissions` 取 ——
 * 抄进代码就成了第二份菜单定义。
 *
 * 共 71 个权限点，其中 57 个有 handler 在用。
 */

export const PERM = {
  ANNOUNCE_PUBLISH:      'oa:announce:publish',
  ANNOUNCE_READ:         'oa:announce:read',
  ANNOUNCE_REVOKE:       'oa:announce:revoke',
  ANNOUNCE_STATS:        'oa:announce:stats',
  ASSET_CLAIM:           'oa:asset:claim',
  ASSET_MANAGE:          'oa:asset:manage', // 无 handler，勿渲染入口
  ASSET_READ:            'oa:asset:read',
  ATTENDANCE_ADMIN:      'oa:attendance:admin',
  ATTENDANCE_PUNCH:      'oa:attendance:punch',
  ATTENDANCE_VIEW:       'oa:attendance:view',
  AUDIT_VIEW:            'oa:audit:view', // 需 JIT 提权
  DOC_FLOW_SUBMIT:       'oa:doc-flow:submit',
  DOC_ARCHIVE:           'oa:doc:archive',
  DOC_DRAFT:             'oa:doc:draft',
  DOC_ISSUE:             'oa:doc:issue',
  DOC_READ:              'oa:doc:read',
  EMPLOYEE_CREATE:       'oa:employee:create',
  EMPLOYEE_EXPORT:       'oa:employee:export', // 无 handler，勿渲染入口 · 需 JIT 提权
  EMPLOYEE_LEAVE:        'oa:employee:leave',
  EMPLOYEE_TRANSFER:     'oa:employee:transfer',
  EMPLOYEE_UPDATE:       'oa:employee:update',
  EMPLOYEE_VIEW:         'oa:employee:view',
  FIELD_IDCARD:          'oa:field:idcard',
  FIELD_MOBILE:          'oa:field:mobile',
  FILE_READ:             'oa:file:read',
  FILE_UPLOAD:           'oa:file:upload',
  FLOW_ADMIN:            'oa:flow:admin',
  FLOW_TODO_HANDLE:      'oa:flow:todo:handle',
  FLOW_TODO_VIEW:        'oa:flow:todo:view',
  IAM_ADMIN:             'oa:iam:admin',
  IAM_CHECK:             'oa:iam:check',
  IAM_DELEGATE:          'oa:iam:delegate',
  IAM_ELEVATE:           'oa:iam:elevate',
  IAM_ELEVATION_APPROVE: 'oa:iam:elevation:approve',
  IAM_GRANT:             'oa:iam:grant',
  IAM_IDENTITY_ADMIN:    'oa:iam:identity:admin',
  IAM_IDENTITY_VIEW:     'oa:iam:identity:view',
  IAM_REQUEST:           'oa:iam:request',
  IAM_REVOKE:            'oa:iam:revoke',
  IAM_VIEW:              'oa:iam:view',
  JOB_RUN:               'oa:job:run',
  KB_READ:               'oa:kb:read',
  KB_SHARE:              'oa:kb:share',
  KB_WRITE:              'oa:kb:write',
  LEAVE_APPLY:           'oa:leave:apply',
  LEAVE_GRANT:           'oa:leave:grant',
  LEAVE_VIEW:            'oa:leave:view',
  MENU_ADMIN:            'oa:menu:admin',
  MENU_ATTENDANCE:       'oa:menu:attendance',
  MENU_DOC:              'oa:menu:doc',
  MENU_IAM:              'oa:menu:iam',
  MENU_KB:               'oa:menu:kb',
  MENU_NOTIFY:           'oa:menu:notify',
  MENU_ORG:              'oa:menu:org',
  MENU_REPORT:           'oa:menu:report',
  MENU_WORKBENCH:        'oa:menu:workbench',
  NOTIFY_READ:           'oa:notify:read',
  NOTIFY_SEND:           'oa:notify:send',
  ORG_ADMIN:             'oa:org:admin',
  ORG_CREATE:            'oa:org:create',
  ORG_DISSOLVE:          'oa:org:dissolve', // 需 JIT 提权
  ORG_MOVE:              'oa:org:move',
  ORG_UPDATE:            'oa:org:update',
  ORG_VIEW:              'oa:org:view',
  REPORT_VIEW:           'oa:report:view',
  ROOM_BOOK:             'oa:room:book',
  ROOM_MANAGE:           'oa:room:manage', // 无 handler，勿渲染入口
  SUPPLY_REQUEST:        'oa:supply:request',
  VEHICLE_BOOK:          'oa:vehicle:book',
  VISITOR_INVITE:        'oa:visitor:invite',
  VISITOR_MANAGE:        'oa:visitor:manage',
} as const

/** 全部合法 permCode 的联合类型。写错一个字母就编译不过。 */
export type PermCode = (typeof PERM)[keyof typeof PERM]

/** 用于运行期校验与元测试（A10：页面里的字面量必须 ⊆ 这个集合）。 */
export const ALL_PERM_CODES: ReadonlySet<string> = new Set<string>(Object.values(PERM))

/**
 * 持有永久授权也必须先 JIT 提权才放行的 code。
 * 前端据此**提前**提示"这一步需要提权"，而不是等后端回 3002 才反应 ——
 * 但真正的边界永远是后端那次 3002，这里只是体验。
 */
export const ELEVATION_REQUIRED_CODES: ReadonlySet<string> = new Set<string>([
  'oa:audit:view',
  'oa:employee:export',
  'oa:org:dissolve',
])

/**
 * 目录里已定义、但**没有任何 handler 消费**的 code（后端已标 DISABLED）。
 * 为它们渲染入口 = 用户点了 404，比没有这个按钮更糟。
 */
export const DISABLED_PERM_CODES: ReadonlySet<string> = new Set<string>([
  'oa:asset:manage',
  'oa:employee:export',
  'oa:room:manage',
])
