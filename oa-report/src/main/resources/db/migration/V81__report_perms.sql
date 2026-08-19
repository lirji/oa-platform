-- 报表与审计权限点。
INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation) VALUES
  ('oa:menu:report',  '管理驾驶舱', 'MENU', 'report', true, false),
  ('oa:report:view',  '查看报表',   'API',  'report', true, false),
  -- ★ require_elevation=true：审计日志里有全公司谁做过什么，是权限最高的一类只读数据。
  --   持有永久授权还不够，必须走一次 JIT 提权 —— 让"我现在要查审计"成为一个
  --   有记录、有时限的动作，而不是某个人默默常驻的能力。
  ('oa:audit:view',   '查看审计日志','API', 'report', true, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'HR_ADMIN' AND p.code IN ('oa:menu:report','oa:report:view')
ON CONFLICT DO NOTHING;

INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'SUPER_ADMIN' AND p.code IN ('oa:menu:report','oa:report:view','oa:audit:view')
ON CONFLICT DO NOTHING;
