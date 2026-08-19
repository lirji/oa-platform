-- 文件与跑批权限点。放在各自服务的迁移里会引入启动顺序依赖（要求 oa_iam 先建好），
-- 但 file/job 的迁移本来就晚于 oa-app 的 V10；这里加一道存在性保护，谁先跑都不会炸。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'oa_iam' AND table_name = 'permission') THEN
        INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation) VALUES
          ('oa:file:upload', '上传文件', 'API', 'file', true, false),
          ('oa:file:read',   '下载文件', 'API', 'file', true, false),
          ('oa:job:run',     '触发跑批', 'API', 'job',  true, false)
        ON CONFLICT (code) DO NOTHING;

        INSERT INTO oa_iam.role_permission (role_id, permission_id)
        SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
         WHERE r.code = 'EMPLOYEE' AND p.code IN ('oa:file:upload','oa:file:read')
        ON CONFLICT DO NOTHING;

        INSERT INTO oa_iam.role_permission (role_id, permission_id)
        SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
         WHERE r.code = 'SUPER_ADMIN' AND p.code = 'oa:job:run'
        ON CONFLICT DO NOTHING;
    END IF;
END $$;
