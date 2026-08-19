-- 考勤域权限点，与 @RequiresPerm 一一对应
INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation) VALUES
  ('oa:menu:attendance',  '考勤',         'MENU', 'attendance', true, false),
  ('oa:attendance:punch', '打卡',         'API',  'attendance', true, false),
  ('oa:attendance:view',  '查看考勤',     'API',  'attendance', true, false),
  ('oa:attendance:admin', '考勤运维/跑批','API',  'attendance', true, false)
ON CONFLICT (code) DO NOTHING;

-- 打卡和看自己的考勤是每个员工的基本能力
INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'EMPLOYEE' AND p.code IN ('oa:menu:attendance','oa:attendance:punch','oa:attendance:view')
ON CONFLICT DO NOTHING;

INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'HR_ADMIN' AND p.code = 'oa:attendance:admin'
ON CONFLICT DO NOTHING;
