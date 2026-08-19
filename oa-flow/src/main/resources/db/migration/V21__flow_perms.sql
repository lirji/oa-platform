-- 审批域权限点。与 oa-flow 的 @RequiresPerm 一一对应 ——
-- 少一条，对应接口就永远判不过（判权时找不到 code 一律拒绝，不会静默放行）。
INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation) VALUES
  ('oa:menu:workbench',   '工作台',       'MENU',  'flow', true, false),
  ('oa:flow:todo:view',   '查看待办',     'API',   'flow', true, false),
  ('oa:flow:todo:handle', '办理待办',     'API',   'flow', true, false),
  ('oa:flow:admin',       '审批域运维',   'API',   'flow', true, false),
  ('oa:leave:apply',      '提交请假申请', 'API',   'flow', true, false),
  ('oa:leave:view',       '查看请假单',   'API',   'flow', true, false),
  ('oa:leave:grant',      '发放假期额度', 'API',   'flow', true, false)
ON CONFLICT (code) DO NOTHING;

-- 提单、看待办、办待办都是员工的基本能力
INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'EMPLOYEE'
   AND p.code IN ('oa:menu:workbench','oa:flow:todo:view','oa:flow:todo:handle','oa:leave:apply','oa:leave:view')
ON CONFLICT DO NOTHING;

-- 发放额度是 HR 职能
INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'HR_ADMIN' AND p.code IN ('oa:leave:grant','oa:flow:admin')
ON CONFLICT DO NOTHING;
