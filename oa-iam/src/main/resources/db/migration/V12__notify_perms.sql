-- 通知域权限点，与 oa-notify-service 的 @RequiresPerm 一一对应。
--
-- ★ 为什么放在 oa-iam 的迁移里而不是 notify 自己的：
--   权限点目录是【全局唯一】的一张表（oa_iam.permission），目录里没有的 code
--   一律判为拒绝。notify 自己的 Flyway 用独立历史表、也不保证在 oa-app 之后跑，
--   由它去写 oa_iam 会引入启动顺序依赖。谁拥有那张表，谁负责往里加行。

INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation) VALUES
  ('oa:menu:notify',      '消息中心',       'MENU', 'notify', true, false),
  ('oa:notify:read',      '查看我的站内信', 'API',  'notify', true, false),
  ('oa:notify:send',      '发送站内信',     'API',  'notify', true, false),
  ('oa:announce:read',    '查看公告',       'API',  'notify', true, false),
  ('oa:announce:publish', '发布公告',       'API',  'notify', true, false),
  ('oa:announce:revoke',  '撤回公告',       'API',  'notify', true, false),
  ('oa:announce:stats',   '公告已读统计',   'API',  'notify', true, false)
ON CONFLICT (code) DO NOTHING;

-- 看自己的消息、看公告、标已读是每个员工的基本能力
INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'EMPLOYEE'
   AND p.code IN ('oa:menu:notify','oa:notify:read','oa:announce:read')
ON CONFLICT DO NOTHING;

-- 发公告/撤公告是行政职能。发站内信是系统间调用（工作台待办提醒等），归管理员。
INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'HR_ADMIN'
   AND p.code IN ('oa:announce:publish','oa:announce:revoke','oa:announce:stats','oa:notify:send')
ON CONFLICT DO NOTHING;
