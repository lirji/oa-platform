-- 通用单据权限点。11 类单据共用一个权限点：提单是每个员工的基本能力，
-- 真正的控制在【审批链】上（谁能批）而不是【谁能提】—— 把提单也按类型分权，
-- 只会让员工连报销单都提不了，然后所有人都来找管理员开权限。
INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation) VALUES
  ('oa:doc-flow:submit', '提交业务单据', 'API', 'flow', true, false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'EMPLOYEE' AND p.code = 'oa:doc-flow:submit'
ON CONFLICT DO NOTHING;
