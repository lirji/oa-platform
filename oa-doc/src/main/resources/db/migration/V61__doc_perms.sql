-- 文档域与行政域的权限点，与 @RequiresPerm 一一对应。
-- 两个域放一起是因为它们同属 Phase 7、同批上线；目录本身不区分来源模块。
INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation) VALUES
  ('oa:menu:doc',       '公文',       'MENU', 'doc',   true, false),
  ('oa:doc:read',       '查看公文',   'API',  'doc',   true, false),
  ('oa:doc:draft',      '拟稿',       'API',  'doc',   true, false),
  ('oa:doc:issue',      '核发公文',   'API',  'doc',   true, false),
  ('oa:doc:archive',    '归档',       'API',  'doc',   true, false),
  ('oa:menu:kb',        '知识库',     'MENU', 'doc',   true, false),
  ('oa:kb:read',        '查看知识库', 'API',  'doc',   true, false),
  ('oa:kb:write',       '编辑知识库', 'API',  'doc',   true, false),
  ('oa:kb:share',       '共享知识库', 'API',  'doc',   true, false),
  ('oa:menu:admin',     '行政',       'MENU', 'admin', true, false),
  ('oa:room:book',      '预定会议室', 'API',  'admin', true, false),
  ('oa:room:manage',    '会议室管理', 'API',  'admin', true, false),
  ('oa:asset:read',     '查看资产',   'API',  'admin', true, false),
  ('oa:asset:claim',    '领用资产',   'API',  'admin', true, false),
  ('oa:asset:manage',   '资产管理',   'API',  'admin', true, false),
  ('oa:supply:request', '领用用品',   'API',  'admin', true, false),
  ('oa:vehicle:book',   '预定车辆',   'API',  'admin', true, false),
  ('oa:visitor:invite', '邀请访客',   'API',  'admin', true, false),
  ('oa:visitor:manage', '访客管理',   'API',  'admin', true, false)
ON CONFLICT (code) DO NOTHING;

-- 员工的基本能力：看公文、看知识库、订会议室/车、领用品、邀访客
INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'EMPLOYEE' AND p.code IN (
   'oa:menu:doc','oa:doc:read','oa:menu:kb','oa:kb:read','oa:menu:admin',
   'oa:room:book','oa:asset:read','oa:asset:claim','oa:supply:request',
   'oa:vehicle:book','oa:visitor:invite')
ON CONFLICT DO NOTHING;

-- 拟稿是岗位职能，核发/归档/资产管理/访客管理是行政职能
INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'HR_ADMIN' AND p.code IN (
   'oa:doc:draft','oa:doc:issue','oa:doc:archive','oa:kb:write','oa:kb:share',
   'oa:room:manage','oa:asset:manage','oa:visitor:manage')
ON CONFLICT DO NOTHING;
