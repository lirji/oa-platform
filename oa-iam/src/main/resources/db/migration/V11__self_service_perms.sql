-- 两个权限点归属修正（Phase 2 冒烟暴露出来的设计问题）。
--
-- ① 委托代理是【员工自助】行为 —— "我出差了，待办交给同事" 不需要管理员批准，
--    原先只给 SUPER_ADMIN 是把自助功能锁死了。
-- ② JIT 提权同理是自助申请，但它的安全闸门不在"谁能调这个接口"，
--    而在"只能激活你【本来就持有】的角色"（见 GrantService.elevate）——
--    就像 sudo：不在 sudoers 里的人，能执行 sudo 命令也没用。

INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation)
VALUES ('oa:iam:elevate', '申请临时提权', 'API', 'iam', true, false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'EMPLOYEE' AND p.code IN ('oa:iam:delegate', 'oa:iam:elevate')
ON CONFLICT DO NOTHING;
