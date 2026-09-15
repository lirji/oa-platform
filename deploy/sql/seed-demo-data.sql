-- 本地演示数据（幂等）。
--
-- 不替代 deploy/scripts/seed-console-fixture.sh 的万人 COPY 装载。
-- 本脚本给控制台工作台 / 组织树 / 通讯录 / 知识库补一批能看见的业务行，
-- 并把当前 SUPER_ADMIN 的 Casdoor sub 写成员工档案，JWT 登录后才能命中「我的待办」。
--
-- 可重复执行：演示行用 DEMO- / 【演示】 / device_id=DEMO-SEED / dedup_key=demo: 识别。

BEGIN;

DO $$
DECLARE
    admin_sub   varchar(64);
    annual_id   bigint;
    sick_id     bigint;
    personal_id bigint;
    e001 bigint; e002 bigint; e003 bigint; e004 bigint; e005 bigint;
    e006 bigint; e007 bigint; e008 bigint; e009 bigint; e010 bigint;
    e011 bigint; e012 bigint;
    e013 bigint; e014 bigint; e015 bigint; e016 bigint; e017 bigint;
    e018 bigint; e019 bigint; e020 bigint; e021 bigint; e022 bigint;
    e023 bigint; e024 bigint;
    p_dir bigint; p_hrd bigint; p_adm bigint; p_eng bigint; p_hr bigint; p_int bigint;
    p_pd bigint; p_pm bigint; p_fin bigint;
    org_group bigint := 910001;
    org_co    bigint := 910002;
    org_rd    bigint := 910003;
    org_hr    bigint := 910004;
    org_adm   bigint := 910005;
    org_iam   bigint := 910006;
    org_rec   bigint := 910007;
    org_pd    bigint := 910008;
    org_fin   bigint := 910009;
    org_pdt   bigint := 910010;
    org_cash  bigint := 910011;
    path_group varchar := '/910001/';
    path_co    varchar := '/910001/910002/';
    path_rd    varchar := '/910001/910002/910003/';
    path_hr    varchar := '/910001/910002/910004/';
    path_adm   varchar := '/910001/910002/910005/';
    path_iam   varchar := '/910001/910002/910003/910006/';
    path_rec   varchar := '/910001/910002/910004/910007/';
    path_pd    varchar := '/910001/910002/910008/';
    path_fin   varchar := '/910001/910002/910009/';
    path_pdt   varchar := '/910001/910002/910008/910010/';
    path_cash  varchar := '/910001/910002/910009/910011/';
    r_emp bigint;
    r_hr  bigint;
    r_mgr bigint;
    room_a bigint;
    room_b bigint;
    room_c bigint;
    veh_id bigint;
    veh_id2 bigint;
    supply_a4 bigint;
    supply_pen bigint;
    inst_leave bigint;
    inst_travel bigint;
    inst_reim bigint;
    kb_folder_id bigint := 910101;
    kb_folder_fin bigint := 910102;
    d date;
    n int;
BEGIN
    -- JWT 登录用 Casdoor UUID；库里可能还留着 DEV 时代的 seed-user-1 SUPER_ADMIN。
    -- 优先绑 UUID，避免和下面 seed-user-1「李四」在同一条 INSERT 里撞 user_id。
    admin_sub := COALESCE(
        (SELECT g.subject_id
           FROM oa_iam.grant_record g
           JOIN oa_iam.role r ON r.id = g.role_id
          WHERE r.code = 'SUPER_ADMIN'
            AND g.subject_type = 'USER'
            AND g.revoked_at IS NULL
            AND g.subject_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          ORDER BY g.id
          LIMIT 1),
        (SELECT g.subject_id
           FROM oa_iam.grant_record g
           JOIN oa_iam.role r ON r.id = g.role_id
          WHERE r.code = 'SUPER_ADMIN'
            AND g.subject_type = 'USER'
            AND g.revoked_at IS NULL
          ORDER BY g.id
          LIMIT 1),
        'd9541ad8-282d-498f-a8d2-ce1d14dd0031');

    SELECT id INTO STRICT annual_id   FROM oa_flow.leave_type WHERE code = 'ANNUAL';
    SELECT id INTO STRICT sick_id     FROM oa_flow.leave_type WHERE code = 'SICK';
    SELECT id INTO STRICT personal_id FROM oa_flow.leave_type WHERE code = 'PERSONAL';
    SELECT id INTO STRICT r_emp FROM oa_iam.role WHERE code = 'EMPLOYEE';
    SELECT id INTO STRICT r_hr  FROM oa_iam.role WHERE code = 'HR_ADMIN';
    SELECT id INTO STRICT r_mgr FROM oa_iam.role WHERE code = 'DEPT_MANAGER';
    SELECT id INTO STRICT room_a FROM oa_admin.meeting_room WHERE code = 'R-A101';
    SELECT id INTO STRICT room_b FROM oa_admin.meeting_room WHERE code = 'R-A201';
    SELECT id INTO STRICT room_c FROM oa_admin.meeting_room WHERE code = 'R-B301';
    SELECT id INTO STRICT veh_id FROM oa_admin.vehicle WHERE plate_no = '京A·12345';
    SELECT id INTO STRICT veh_id2 FROM oa_admin.vehicle WHERE plate_no = '京A·67890';
    SELECT id INTO STRICT supply_a4 FROM oa_admin.supply WHERE code = 'SUP-A4';
    SELECT id INTO STRICT supply_pen FROM oa_admin.supply WHERE code = 'SUP-PEN';

    -- ── 组织树（固定 id，路径稳定，控制台 maxDepth=2 能看到集团/公司/部门）
    INSERT INTO oa_org.org_unit
        (id, tenant_id, parent_id, code, name, type, path, depth, sort_order, status, leader_user_id, created_by)
    VALUES
        (org_group, 1, NULL,      'DEMO-GROUP',   '演示集团',     'GROUP',   path_group, 0, 0, 'ACTIVE', admin_sub, 'seed-demo'),
        (org_co,    1, org_group, 'DEMO-COMPANY', '华东公司',     'COMPANY', path_co,    1, 0, 'ACTIVE', admin_sub, 'seed-demo'),
        (org_rd,    1, org_co,    'DEMO-DEPT-RD', '平台研发部',   'DEPT',    path_rd,    2, 0, 'ACTIVE', admin_sub, 'seed-demo'),
        (org_hr,    1, org_co,    'DEMO-DEPT-HR', '人力资源部',   'DEPT',    path_hr,    2, 1, 'ACTIVE', NULL,      'seed-demo'),
        (org_adm,   1, org_co,    'DEMO-DEPT-AD', '行政部',       'DEPT',    path_adm,   2, 2, 'ACTIVE', NULL,      'seed-demo'),
        (org_iam,   1, org_rd,    'DEMO-TEAM-IAM','权限组',       'TEAM',    path_iam,   3, 0, 'ACTIVE', NULL,      'seed-demo'),
        (org_rec,   1, org_hr,    'DEMO-TEAM-REC','招聘组',       'TEAM',    path_rec,   3, 0, 'ACTIVE', NULL,      'seed-demo'),
        (org_pd,    1, org_co,    'DEMO-DEPT-PD', '产品部',       'DEPT',    path_pd,    2, 3, 'ACTIVE', NULL,      'seed-demo'),
        (org_fin,   1, org_co,    'DEMO-DEPT-FN', '财务部',       'DEPT',    path_fin,   2, 4, 'ACTIVE', NULL,      'seed-demo'),
        (org_pdt,   1, org_pd,    'DEMO-TEAM-PD', '产品一组',     'TEAM',    path_pdt,   3, 0, 'ACTIVE', NULL,      'seed-demo'),
        (org_cash,  1, org_fin,   'DEMO-TEAM-FN', '出纳组',       'TEAM',    path_cash,  3, 0, 'ACTIVE', NULL,      'seed-demo')
    ON CONFLICT (id) DO UPDATE
        SET name = EXCLUDED.name,
            parent_id = EXCLUDED.parent_id,
            type = EXCLUDED.type,
            path = EXCLUDED.path,
            depth = EXCLUDED.depth,
            sort_order = EXCLUDED.sort_order,
            leader_user_id = EXCLUDED.leader_user_id,
            status = 'ACTIVE',
            updated_at = now();

    DELETE FROM oa_org.org_closure
     WHERE ancestor_id BETWEEN 910001 AND 910011
        OR descendant_id BETWEEN 910001 AND 910011;

    INSERT INTO oa_org.org_closure(ancestor_id, descendant_id, distance) VALUES
        (910001,910001,0),(910001,910002,1),(910001,910003,2),(910001,910004,2),
        (910001,910005,2),(910001,910006,3),(910001,910007,3),
        (910001,910008,2),(910001,910009,2),(910001,910010,3),(910001,910011,3),
        (910002,910002,0),(910002,910003,1),(910002,910004,1),(910002,910005,1),
        (910002,910006,2),(910002,910007,2),(910002,910008,1),(910002,910009,1),
        (910002,910010,2),(910002,910011,2),
        (910003,910003,0),(910003,910006,1),
        (910004,910004,0),(910004,910007,1),
        (910005,910005,0),
        (910006,910006,0),
        (910007,910007,0),
        (910008,910008,0),(910008,910010,1),
        (910009,910009,0),(910009,910011,1),
        (910010,910010,0),
        (910011,910011,0);

    INSERT INTO oa_org.job_position(tenant_id, code, name, job_family, job_level, is_manager, status)
    VALUES
        (1,'DEMO-P-DIR','技术总监','研发','M3', true,  'ACTIVE'),
        (1,'DEMO-P-HRD','人力资源负责人','职能','M2', true,  'ACTIVE'),
        (1,'DEMO-P-ADM','行政主管','职能','M1', true,  'ACTIVE'),
        (1,'DEMO-P-ENG','软件工程师','研发','P5', false, 'ACTIVE'),
        (1,'DEMO-P-HR', '人事专员','职能','P4', false, 'ACTIVE'),
        (1,'DEMO-P-INT','实习生','职能','I1', false, 'ACTIVE'),
        (1,'DEMO-P-PD', '产品总监','产品','M3', true,  'ACTIVE'),
        (1,'DEMO-P-PM', '产品经理','产品','P6', false, 'ACTIVE'),
        (1,'DEMO-P-FIN','财务专员','职能','P4', false, 'ACTIVE')
    ON CONFLICT (tenant_id, code) DO UPDATE
        SET name = EXCLUDED.name, is_manager = EXCLUDED.is_manager, status = 'ACTIVE';

    SELECT id INTO STRICT p_dir FROM oa_org.job_position WHERE code = 'DEMO-P-DIR';
    SELECT id INTO STRICT p_hrd FROM oa_org.job_position WHERE code = 'DEMO-P-HRD';
    SELECT id INTO STRICT p_adm FROM oa_org.job_position WHERE code = 'DEMO-P-ADM';
    SELECT id INTO STRICT p_eng FROM oa_org.job_position WHERE code = 'DEMO-P-ENG';
    SELECT id INTO STRICT p_hr  FROM oa_org.job_position WHERE code = 'DEMO-P-HR';
    SELECT id INTO STRICT p_int FROM oa_org.job_position WHERE code = 'DEMO-P-INT';
    SELECT id INTO STRICT p_pd  FROM oa_org.job_position WHERE code = 'DEMO-P-PD';
    SELECT id INTO STRICT p_pm  FROM oa_org.job_position WHERE code = 'DEMO-P-PM';
    SELECT id INTO STRICT p_fin FROM oa_org.job_position WHERE code = 'DEMO-P-FIN';

    -- ── 员工：JWT 管理员必须落成 employee.user_id，否则工作台按无档案身份跑
    INSERT INTO oa_org.employee
        (tenant_id, user_id, emp_no, name, email, gender, hire_date, employment_type, status, created_by)
    VALUES
        (1, admin_sub,     'DEMO-E001', '陈默', 'chenmo@demo.local',  'M', DATE '2022-03-01', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-1', 'DEMO-E002', '李四', 'lisi@demo.local',    'M', DATE '2023-04-01', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-2', 'DEMO-E003', '王芳', 'wangfang@demo.local','F', DATE '2021-07-12', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-3', 'DEMO-E004', '赵强', 'zhaoqiang@demo.local','M', DATE '2020-11-08', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-4', 'DEMO-E005', '周可', 'zhouke@demo.local',  'F', DATE '2024-01-15', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-5', 'DEMO-E006', '孙宁', 'sunning@demo.local', 'M', DATE '2026-07-01', 'INTERN',    'PROBATION','seed-demo'),
        (1, 'seed-user-6', 'DEMO-E007', '吴岚', 'wulan@demo.local',   'F', DATE '2023-09-01', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-7', 'DEMO-E008', '郑博', 'zhengbo@demo.local', 'M', DATE '2022-12-01', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-8', 'DEMO-E009', '冯悦', 'fengyue@demo.local', 'F', DATE '2024-05-20', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-9', 'DEMO-E010', '韩磊', 'hanlei@demo.local',  'M', DATE '2025-02-10', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-10','DEMO-E011', '曹薇', 'caowei@demo.local',  'F', DATE '2023-06-18', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'e2e-no-perm', 'DEMO-E012', '丁凯', 'dingkai@demo.local', 'M', DATE '2026-08-01', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-11','DEMO-E013', '潘琪', 'panqi@demo.local',   'F', DATE '2021-05-01', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-12','DEMO-E014', '蒋川', 'jiangchuan@demo.local','M', DATE '2023-08-08', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-13','DEMO-E015', '沈悦', 'shenyue@demo.local', 'F', DATE '2024-02-14', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-14','DEMO-E016', '姚舟', 'yaozhou@demo.local', 'M', DATE '2019-09-01', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-15','DEMO-E017', '卢敏', 'lumin@demo.local',   'F', DATE '2022-04-18', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-16','DEMO-E018', '钱程', 'qiancheng@demo.local','M', DATE '2025-06-01', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-17','DEMO-E019', '唐宁', 'tangning@demo.local', 'F', DATE '2023-01-09', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-18','DEMO-E020', '魏岚', 'weilan@demo.local',  'F', DATE '2024-10-12', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-19','DEMO-E021', '邓浩', 'denghao@demo.local', 'M', DATE '2022-08-20', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-20','DEMO-E022', '蔡琪', 'caiqi@demo.local',   'F', DATE '2025-11-03', 'FULL_TIME', 'ACTIVE', 'seed-demo'),
        (1, 'seed-user-21','DEMO-E023', '彭飞', 'pengfei@demo.local', 'M', DATE '2026-03-01', 'FULL_TIME', 'PROBATION','seed-demo'),
        (1, 'seed-user-22','DEMO-E024', '夏荷', 'xiahe@demo.local',   'F', DATE '2026-07-15', 'INTERN',    'PROBATION','seed-demo')
    ON CONFLICT (user_id) DO UPDATE
        SET name = EXCLUDED.name,
            emp_no = EXCLUDED.emp_no,
            email = EXCLUDED.email,
            gender = EXCLUDED.gender,
            employment_type = EXCLUDED.employment_type,
            status = EXCLUDED.status,
            updated_at = now();

    SELECT id INTO STRICT e001 FROM oa_org.employee WHERE emp_no = 'DEMO-E001';
    SELECT id INTO STRICT e002 FROM oa_org.employee WHERE emp_no = 'DEMO-E002';
    SELECT id INTO STRICT e003 FROM oa_org.employee WHERE emp_no = 'DEMO-E003';
    SELECT id INTO STRICT e004 FROM oa_org.employee WHERE emp_no = 'DEMO-E004';
    SELECT id INTO STRICT e005 FROM oa_org.employee WHERE emp_no = 'DEMO-E005';
    SELECT id INTO STRICT e006 FROM oa_org.employee WHERE emp_no = 'DEMO-E006';
    SELECT id INTO STRICT e007 FROM oa_org.employee WHERE emp_no = 'DEMO-E007';
    SELECT id INTO STRICT e008 FROM oa_org.employee WHERE emp_no = 'DEMO-E008';
    SELECT id INTO STRICT e009 FROM oa_org.employee WHERE emp_no = 'DEMO-E009';
    SELECT id INTO STRICT e010 FROM oa_org.employee WHERE emp_no = 'DEMO-E010';
    SELECT id INTO STRICT e011 FROM oa_org.employee WHERE emp_no = 'DEMO-E011';
    SELECT id INTO STRICT e012 FROM oa_org.employee WHERE emp_no = 'DEMO-E012';
    SELECT id INTO STRICT e013 FROM oa_org.employee WHERE emp_no = 'DEMO-E013';
    SELECT id INTO STRICT e014 FROM oa_org.employee WHERE emp_no = 'DEMO-E014';
    SELECT id INTO STRICT e015 FROM oa_org.employee WHERE emp_no = 'DEMO-E015';
    SELECT id INTO STRICT e016 FROM oa_org.employee WHERE emp_no = 'DEMO-E016';
    SELECT id INTO STRICT e017 FROM oa_org.employee WHERE emp_no = 'DEMO-E017';
    SELECT id INTO STRICT e018 FROM oa_org.employee WHERE emp_no = 'DEMO-E018';
    SELECT id INTO STRICT e019 FROM oa_org.employee WHERE emp_no = 'DEMO-E019';
    SELECT id INTO STRICT e020 FROM oa_org.employee WHERE emp_no = 'DEMO-E020';
    SELECT id INTO STRICT e021 FROM oa_org.employee WHERE emp_no = 'DEMO-E021';
    SELECT id INTO STRICT e022 FROM oa_org.employee WHERE emp_no = 'DEMO-E022';
    SELECT id INTO STRICT e023 FROM oa_org.employee WHERE emp_no = 'DEMO-E023';
    SELECT id INTO STRICT e024 FROM oa_org.employee WHERE emp_no = 'DEMO-E024';

    UPDATE oa_org.org_unit SET leader_user_id = (SELECT user_id FROM oa_org.employee WHERE id = e003)
     WHERE id = org_hr;
    UPDATE oa_org.org_unit SET leader_user_id = (SELECT user_id FROM oa_org.employee WHERE id = e004)
     WHERE id = org_adm;

    UPDATE oa_org.org_unit SET leader_user_id = (SELECT user_id FROM oa_org.employee WHERE id = e013)
     WHERE id = org_pd;
    UPDATE oa_org.org_unit SET leader_user_id = (SELECT user_id FROM oa_org.employee WHERE id = e016)
     WHERE id = org_fin;

    DELETE FROM oa_org.reporting_line
     WHERE employee_id IN (e001,e002,e003,e004,e005,e006,e007,e008,e009,e010,e011,e012,
                           e013,e014,e015,e016,e017,e018,e019,e020,e021,e022,e023,e024);
    DELETE FROM oa_org.employee_org_assignment
     WHERE employee_id IN (e001,e002,e003,e004,e005,e006,e007,e008,e009,e010,e011,e012,
                           e013,e014,e015,e016,e017,e018,e019,e020,e021,e022,e023,e024);

    INSERT INTO oa_org.employee_org_assignment
        (tenant_id, employee_id, org_unit_id, position_id, assignment_type, is_leader, valid_from, created_by)
    VALUES
        (1, e001, org_rd,  p_dir, 'PRIMARY', true,  DATE '2022-03-01', 'seed-demo'),
        (1, e002, org_iam, p_eng, 'PRIMARY', true,  DATE '2023-04-01', 'seed-demo'),
        (1, e003, org_hr,  p_hrd, 'PRIMARY', true,  DATE '2021-07-12', 'seed-demo'),
        (1, e004, org_adm, p_adm, 'PRIMARY', true,  DATE '2020-11-08', 'seed-demo'),
        (1, e005, org_iam, p_eng, 'PRIMARY', false, DATE '2024-01-15', 'seed-demo'),
        (1, e006, org_rec, p_int, 'PRIMARY', false, DATE '2026-07-01', 'seed-demo'),
        (1, e007, org_adm, p_adm, 'PRIMARY', false, DATE '2023-09-01', 'seed-demo'),
        (1, e008, org_rd,  p_eng, 'PRIMARY', false, DATE '2022-12-01', 'seed-demo'),
        (1, e009, org_hr,  p_hr,  'PRIMARY', false, DATE '2024-05-20', 'seed-demo'),
        (1, e010, org_iam, p_eng, 'PRIMARY', false, DATE '2025-02-10', 'seed-demo'),
        (1, e011, org_adm, p_eng, 'PRIMARY', false, DATE '2023-06-18', 'seed-demo'),
        (1, e012, org_rec, p_hr,  'PRIMARY', false, DATE '2026-08-01', 'seed-demo'),
        (1, e013, org_pd,  p_pd,  'PRIMARY', true,  DATE '2021-05-01', 'seed-demo'),
        (1, e014, org_pdt, p_pm,  'PRIMARY', true,  DATE '2023-08-08', 'seed-demo'),
        (1, e015, org_pdt, p_pm,  'PRIMARY', false, DATE '2024-02-14', 'seed-demo'),
        (1, e016, org_fin, p_fin, 'PRIMARY', true,  DATE '2019-09-01', 'seed-demo'),
        (1, e017, org_cash,p_fin, 'PRIMARY', true,  DATE '2022-04-18', 'seed-demo'),
        (1, e018, org_cash,p_fin, 'PRIMARY', false, DATE '2025-06-01', 'seed-demo'),
        (1, e019, org_rd,  p_eng, 'PRIMARY', false, DATE '2023-01-09', 'seed-demo'),
        (1, e020, org_hr,  p_hr,  'PRIMARY', false, DATE '2024-10-12', 'seed-demo'),
        (1, e021, org_adm, p_adm, 'PRIMARY', false, DATE '2022-08-20', 'seed-demo'),
        (1, e022, org_iam, p_eng, 'PRIMARY', false, DATE '2025-11-03', 'seed-demo'),
        (1, e023, org_rec, p_hr,  'PRIMARY', false, DATE '2026-03-01', 'seed-demo'),
        (1, e024, org_pdt, p_int, 'PRIMARY', false, DATE '2026-07-15', 'seed-demo');

    INSERT INTO oa_org.reporting_line
        (tenant_id, employee_id, manager_employee_id, type, valid_from)
    VALUES
        (1, e002, e001, 'SOLID', DATE '2023-04-01'),
        (1, e003, e001, 'SOLID', DATE '2021-07-12'),
        (1, e004, e001, 'SOLID', DATE '2020-11-08'),
        (1, e005, e002, 'SOLID', DATE '2024-01-15'),
        (1, e006, e003, 'SOLID', DATE '2026-07-01'),
        (1, e007, e004, 'SOLID', DATE '2023-09-01'),
        (1, e008, e001, 'SOLID', DATE '2022-12-01'),
        (1, e009, e003, 'SOLID', DATE '2024-05-20'),
        (1, e010, e002, 'SOLID', DATE '2025-02-10'),
        (1, e011, e004, 'SOLID', DATE '2023-06-18'),
        (1, e012, e003, 'SOLID', DATE '2026-08-01'),
        (1, e013, e001, 'SOLID', DATE '2021-05-01'),
        (1, e014, e013, 'SOLID', DATE '2023-08-08'),
        (1, e015, e014, 'SOLID', DATE '2024-02-14'),
        (1, e016, e001, 'SOLID', DATE '2019-09-01'),
        (1, e017, e016, 'SOLID', DATE '2022-04-18'),
        (1, e018, e017, 'SOLID', DATE '2025-06-01'),
        (1, e019, e001, 'SOLID', DATE '2023-01-09'),
        (1, e020, e003, 'SOLID', DATE '2024-10-12'),
        (1, e021, e004, 'SOLID', DATE '2022-08-20'),
        (1, e022, e002, 'SOLID', DATE '2025-11-03'),
        (1, e023, e003, 'SOLID', DATE '2026-03-01'),
        (1, e024, e014, 'SOLID', DATE '2026-07-15');

    -- ── 清掉上一轮演示业务行
    DELETE FROM oa_flow.leave_balance_txn WHERE request_id LIKE 'DEMO-%';
    DELETE FROM oa_flow.todo_item WHERE task_id LIKE 'DEMO-%';
    DELETE FROM oa_flow.approval_node_log WHERE instance_id IN (
        SELECT id FROM oa_flow.approval_instance WHERE business_key LIKE 'DEMO-%');
    DELETE FROM oa_flow.approval_instance WHERE business_key LIKE 'DEMO-%';
    DELETE FROM oa_flow.leave_request WHERE request_no LIKE 'DEMO-%';
    DELETE FROM oa_flow.business_doc WHERE doc_no LIKE 'DEMO-%';
    DELETE FROM oa_att.punch_record WHERE device_id = 'DEMO-SEED';
    DELETE FROM oa_att.attendance_daily
     WHERE user_id IN (SELECT user_id FROM oa_org.employee WHERE emp_no LIKE 'DEMO-%')
       AND work_date >= current_date - 21;
    DELETE FROM oa_doc.doc_flow_log WHERE doc_id IN (
        SELECT id FROM oa_doc.official_doc WHERE title LIKE '【演示】%');
    DELETE FROM oa_doc.official_doc WHERE title LIKE '【演示】%';
    DELETE FROM oa_doc.kb_share WHERE granted_by = 'seed-demo'
        OR (resource_type = 'FOLDER' AND resource_id IN (kb_folder_id, kb_folder_fin));
    DELETE FROM oa_doc.kb_doc WHERE title LIKE '【演示】%';
    DELETE FROM oa_doc.kb_folder WHERE id IN (kb_folder_id, kb_folder_fin);
    DELETE FROM oa_admin.room_booking WHERE subject LIKE '【演示】%';
    DELETE FROM oa_admin.asset_txn WHERE asset_id IN (
        SELECT id FROM oa_admin.asset WHERE asset_no LIKE 'DEMO-%');
    DELETE FROM oa_admin.asset WHERE asset_no LIKE 'DEMO-%';
    DELETE FROM oa_admin.supply_request WHERE requester_id IN (
        SELECT user_id FROM oa_org.employee WHERE emp_no LIKE 'DEMO-%')
      AND created_at >= now() - interval '30 days';
    DELETE FROM oa_admin.vehicle_booking WHERE purpose LIKE '【演示】%';
    DELETE FROM oa_admin.visitor WHERE company = '演示供应商' OR name LIKE '【演示】%';
    DELETE FROM oa_notify.notification WHERE dedup_key LIKE 'demo:%';
    DELETE FROM oa_notify.announcement WHERE title LIKE '【演示】%';

    -- ── 假期额度 + 请假单
    INSERT INTO oa_flow.leave_balance(tenant_id, user_id, leave_type_id, period, total_days, used_days, frozen_days)
    VALUES
        (1, admin_sub,     annual_id, to_char(current_date,'YYYY'), 15, 3, 2),
        (1, admin_sub,     sick_id,   to_char(current_date,'YYYY'), 10, 0, 0),
        (1, 'seed-user-1', annual_id, to_char(current_date,'YYYY'), 10, 2, 0),
        (1, 'seed-user-2', annual_id, to_char(current_date,'YYYY'), 15, 5, 0),
        (1, 'seed-user-4', annual_id, to_char(current_date,'YYYY'),  8, 0, 1),
        (1, 'seed-user-11', annual_id, to_char(current_date,'YYYY'), 12, 1, 0),
        (1, 'seed-user-12', annual_id, to_char(current_date,'YYYY'), 10, 0, 1),
        (1, 'seed-user-14', annual_id, to_char(current_date,'YYYY'), 15, 4, 0),
        (1, 'seed-user-15', sick_id,   to_char(current_date,'YYYY'), 10, 1, 0)
    ON CONFLICT (tenant_id, user_id, leave_type_id, period) DO UPDATE
        SET total_days = EXCLUDED.total_days,
            used_days = EXCLUDED.used_days,
            frozen_days = EXCLUDED.frozen_days,
            version = oa_flow.leave_balance.version + 1,
            updated_at = now();

    INSERT INTO oa_flow.leave_request
        (tenant_id, request_no, user_id, employee_id, leave_type_id, start_date, end_date, days,
         reason, status, org_id, org_path, decided_at)
    VALUES
        (1,'DEMO-LEAVE-001', admin_sub, e001, annual_id,
            current_date + 7, current_date + 8, 2, '年假：陪家人出游', 'PENDING', org_rd, path_rd, NULL),
        (1,'DEMO-LEAVE-002', 'seed-user-1', e002, annual_id,
            current_date - 20, current_date - 19, 2, '调休后补年假', 'APPROVED', org_iam, path_iam, now() - interval '18 days'),
        (1,'DEMO-LEAVE-003', 'seed-user-4', e005, annual_id,
            current_date + 3, current_date + 3, 1, '家里有事', 'PENDING', org_iam, path_iam, NULL),
        (1,'DEMO-LEAVE-004', 'seed-user-6', e007, sick_id,
            current_date - 5, current_date - 4, 2, '感冒就医', 'APPROVED', org_adm, path_adm, now() - interval '3 days'),
        (1,'DEMO-LEAVE-005', 'seed-user-5', e006, personal_id,
            current_date - 2, current_date - 2, 1, '学校答辩', 'REJECTED', org_rec, path_rec, now() - interval '1 day'),
        (1,'DEMO-LEAVE-006', 'seed-user-12', e014, annual_id,
            current_date + 10, current_date + 12, 3, '回家探亲', 'PENDING', org_pdt, path_pdt, NULL),
        (1,'DEMO-LEAVE-007', 'seed-user-15', e017, sick_id,
            current_date - 8, current_date - 8, 1, '体检复查', 'APPROVED', org_cash, path_cash, now() - interval '6 days'),
        (1,'DEMO-LEAVE-008', 'seed-user-13', e015, personal_id,
            current_date + 1, current_date + 1, 1, '房屋过户', 'PENDING', org_pdt, path_pdt, NULL),
        (1,'DEMO-LEAVE-009', 'seed-user-17', e019, annual_id,
            current_date - 30, current_date - 28, 3, '年假已休', 'APPROVED', org_rd, path_rd, now() - interval '25 days');

    INSERT INTO oa_flow.leave_balance_txn(balance_id, request_id, action, days, remark)
    SELECT b.id, 'DEMO-LEAVE-001', 'FREEZE', 2, '演示：审批中冻结'
      FROM oa_flow.leave_balance b
     WHERE b.user_id = admin_sub AND b.leave_type_id = annual_id
       AND b.period = to_char(current_date,'YYYY')
    ON CONFLICT (request_id, action) DO NOTHING;

    INSERT INTO oa_flow.leave_balance_txn(balance_id, request_id, action, days, remark)
    SELECT b.id, 'DEMO-LEAVE-003', 'FREEZE', 1, '演示：员工请假冻结'
      FROM oa_flow.leave_balance b
     WHERE b.user_id = 'seed-user-4' AND b.leave_type_id = annual_id
       AND b.period = to_char(current_date,'YYYY')
    ON CONFLICT (request_id, action) DO NOTHING;

    INSERT INTO oa_flow.leave_balance_txn(balance_id, request_id, action, days, remark)
    SELECT b.id, 'DEMO-LEAVE-006', 'FREEZE', 3, '演示：产品组请假冻结'
      FROM oa_flow.leave_balance b
     WHERE b.user_id = 'seed-user-12' AND b.leave_type_id = annual_id
       AND b.period = to_char(current_date,'YYYY')
    ON CONFLICT (request_id, action) DO NOTHING;

    -- ── 通用单据（我发起的）
    INSERT INTO oa_flow.business_doc
        (tenant_id, biz_type, doc_no, applicant_id, applicant_name, title, summary, form_data,
         amount, days, status, org_id, org_path, finished_at)
    VALUES
        (1,'OVERTIME','DEMO-OT-001', admin_sub, '陈默', '周末值班加班', '系统割接值班',
         '{"startAt":"2026-09-13 09:00","endAt":"2026-09-13 18:00","days":1,"reason":"生产割接"}'::jsonb,
         NULL, 1, 'APPROVED', org_rd, path_rd, now() - interval '1 day'),
        (1,'TRAVEL','DEMO-TR-001', admin_sub, '陈默', '上海客户现场', '三天驻场',
         '{"startAt":"2026-09-22","endAt":"2026-09-24","days":3,"reason":"客户上线支持"}'::jsonb,
         NULL, 3, 'PENDING', org_rd, path_rd, NULL),
        (1,'REIMBURSE','DEMO-RB-001', admin_sub, '陈默', '差旅报销 3280 元', '交通+住宿',
         '{"amount":3280,"reason":"8 月上海差旅"}'::jsonb,
         3280, NULL, 'APPROVED', org_rd, path_rd, now() - interval '10 days'),
        (1,'TRAVEL','DEMO-TR-002', 'seed-user-1', '李四', '杭州交流', '两天',
         '{"startAt":"2026-09-18","endAt":"2026-09-19","days":2,"reason":"技术交流"}'::jsonb,
         NULL, 2, 'PENDING', org_iam, path_iam, NULL),
        (1,'REIMBURSE','DEMO-RB-002', 'seed-user-3', '赵强', '办公采购报销', '行政物资',
         '{"amount":860,"reason":"打印机硒鼓"}'::jsonb,
         860, NULL, 'PENDING', org_adm, path_adm, NULL),
        (1,'SEAL','DEMO-SEAL-001', admin_sub, '陈默', '对外合作协议用印', '两份合同章',
         '{"reason":"与华东师范合作备忘录"}'::jsonb,
         NULL, NULL, 'PENDING', org_rd, path_rd, NULL),
        (1,'PURCHASE','DEMO-PO-001', admin_sub, '陈默', '采购开发机 2 台', '预算 2.4 万',
         '{"amount":24000,"reason":"权限组扩编"}'::jsonb,
         24000, NULL, 'PENDING', org_rd, path_rd, NULL),
        (1,'COMPENSATORY','DEMO-TO-001', admin_sub, '陈默', '调休 0.5 天', '割接后补休',
         '{"days":0.5,"reason":"周末值班调休"}'::jsonb,
         NULL, 0.5, 'APPROVED', org_rd, path_rd, now() - interval '12 hours'),
        (1,'OVERTIME','DEMO-OT-002', 'seed-user-11', '潘琪', '需求评审加班', '跨部门对齐',
         '{"startAt":"2026-09-12 19:00","endAt":"2026-09-12 22:00","days":0.5,"reason":"评审延期"}'::jsonb,
         NULL, 0.5, 'PENDING', org_pd, path_pd, NULL),
        (1,'LOAN','DEMO-LOAN-001', 'seed-user-14', '姚舟', '备用金借款 8000', '客户招待',
         '{"amount":8000,"reason":"三季度招待备用金"}'::jsonb,
         8000, NULL, 'PENDING', org_fin, path_fin, NULL),
        (1,'CONTRACT','DEMO-CT-001', 'seed-user-13', '沈悦', '供应商框架合同', '一年期',
         '{"amount":180000,"reason":"设计外包"}'::jsonb,
         180000, NULL, 'PENDING', org_pdt, path_pdt, NULL);

    INSERT INTO oa_flow.approval_instance
        (tenant_id, biz_type, business_key, process_definition_key, process_instance_id,
         applicant_user_id, applicant_employee_id, applicant_name, org_id, org_path,
         status, submitted_at)
    VALUES
        (1,'LEAVE','DEMO-LEAVE-003','oa.leave','demo-pi-leave-003',
         'seed-user-4', e005, '周可', org_iam, path_iam, 'RUNNING', now() - interval '6 hours'),
        (1,'TRAVEL','DEMO-TR-002','oa.travel','demo-pi-tr-002',
         'seed-user-1', e002, '李四', org_iam, path_iam, 'RUNNING', now() - interval '4 hours'),
        (1,'REIMBURSE','DEMO-RB-002','oa.reimburse','demo-pi-rb-002',
         'seed-user-3', e004, '赵强', org_adm, path_adm, 'RUNNING', now() - interval '2 hours'),
        (1,'LEAVE','DEMO-LEAVE-001','oa.leave','demo-pi-leave-001',
         admin_sub, e001, '陈默', org_rd, path_rd, 'RUNNING', now() - interval '1 hour'),
        (1,'LEAVE','DEMO-LEAVE-006','oa.leave','demo-pi-leave-006',
         'seed-user-12', e014, '蒋川', org_pdt, path_pdt, 'RUNNING', now() - interval '5 hours'),
        (1,'LEAVE','DEMO-LEAVE-008','oa.leave','demo-pi-leave-008',
         'seed-user-13', e015, '沈悦', org_pdt, path_pdt, 'RUNNING', now() - interval '3 hours'),
        (1,'OVERTIME','DEMO-OT-002','oa.overtime','demo-pi-ot-002',
         'seed-user-11', e013, '潘琪', org_pd, path_pd, 'RUNNING', now() - interval '8 hours'),
        (1,'LOAN','DEMO-LOAN-001','oa.loan','demo-pi-loan-001',
         'seed-user-14', e016, '姚舟', org_fin, path_fin, 'RUNNING', now() - interval '90 minutes'),
        (1,'CONTRACT','DEMO-CT-001','oa.contract','demo-pi-ct-001',
         'seed-user-13', e015, '沈悦', org_pdt, path_pdt, 'RUNNING', now() - interval '50 minutes');

    SELECT id INTO STRICT inst_leave  FROM oa_flow.approval_instance WHERE business_key = 'DEMO-LEAVE-003';
    SELECT id INTO STRICT inst_travel FROM oa_flow.approval_instance WHERE business_key = 'DEMO-TR-002';
    SELECT id INTO STRICT inst_reim   FROM oa_flow.approval_instance WHERE business_key = 'DEMO-RB-002';

    INSERT INTO oa_flow.todo_item
        (tenant_id, task_id, process_instance_id, instance_id, biz_type, title, summary,
         applicant_user_id, applicant_name, assignee_user_id, state, org_id, org_path, due_at)
    VALUES
        (1,'DEMO-TASK-LEAVE-003','demo-pi-leave-003', inst_leave, 'LEAVE',
         '周可 · 年假 1 天', '家里有事', 'seed-user-4', '周可', admin_sub,
         'PENDING', org_iam, path_iam, now() + interval '2 days'),
        (1,'DEMO-TASK-TR-002','demo-pi-tr-002', inst_travel, 'TRAVEL',
         '李四 · 杭州交流', '两天驻场', 'seed-user-1', '李四', admin_sub,
         'PENDING', org_iam, path_iam, now() + interval '1 day'),
        (1,'DEMO-TASK-RB-002','demo-pi-rb-002', inst_reim, 'REIMBURSE',
         '赵强 · 报销 860 元', '打印机硒鼓', 'seed-user-3', '赵强', admin_sub,
         'PENDING', org_adm, path_adm, now() + interval '3 days');

    INSERT INTO oa_flow.todo_item
        (tenant_id, task_id, process_instance_id, instance_id, biz_type, title, summary,
         applicant_user_id, applicant_name, assignee_user_id, state, org_id, org_path, due_at)
    SELECT 1, 'DEMO-TASK-' || i.business_key, i.process_instance_id, i.id, i.biz_type,
           CASE i.business_key
             WHEN 'DEMO-LEAVE-006' THEN '蒋川 · 年假 3 天'
             WHEN 'DEMO-LEAVE-008' THEN '沈悦 · 事假 1 天'
             WHEN 'DEMO-OT-002'    THEN '潘琪 · 加班 0.5 天'
             WHEN 'DEMO-LOAN-001'  THEN '姚舟 · 借款 8000 元'
             WHEN 'DEMO-CT-001'    THEN '沈悦 · 供应商合同'
           END,
           CASE i.business_key
             WHEN 'DEMO-LEAVE-006' THEN '回家探亲'
             WHEN 'DEMO-LEAVE-008' THEN '房屋过户'
             WHEN 'DEMO-OT-002'    THEN '需求评审加班'
             WHEN 'DEMO-LOAN-001'  THEN '客户招待备用金'
             WHEN 'DEMO-CT-001'    THEN '一年期框架'
           END,
           i.applicant_user_id, i.applicant_name, admin_sub, 'PENDING', i.org_id, i.org_path,
           now() + interval '2 days'
      FROM oa_flow.approval_instance i
     WHERE i.business_key IN ('DEMO-LEAVE-006','DEMO-LEAVE-008','DEMO-OT-002','DEMO-LOAN-001','DEMO-CT-001');

    -- ── 考勤：最近 5 个工作日
    n := 0;
    FOR d IN
        SELECT gs::date
          FROM generate_series(current_date - 14, current_date - 1, interval '1 day') gs
         WHERE extract(isodow FROM gs) < 6
         ORDER BY 1 DESC
         LIMIT 5
    LOOP
        n := n + 1;
        INSERT INTO oa_att.punch_record
            (tenant_id, user_id, employee_id, punch_date, punch_type, punch_time, source,
             latitude, longitude, device_id, org_id, org_path)
        VALUES
            (1, admin_sub, e001, d, 'IN',
             d::timestamp + CASE WHEN n = 1 THEN time '09:18' ELSE time '08:52' END,
             'WEB', 39.904200, 116.407400, 'DEMO-SEED', org_rd, path_rd),
            (1, admin_sub, e001, d, 'OUT',
             d::timestamp + time '18:06',
             'WEB', 39.904200, 116.407400, 'DEMO-SEED', org_rd, path_rd),
            (1, 'seed-user-1', e002, d, 'IN',
             d::timestamp + time '08:58',
             'MOBILE', 39.904800, 116.408000, 'DEMO-SEED', org_iam, path_iam),
            (1, 'seed-user-1', e002, d, 'OUT',
             d::timestamp + time '18:12',
             'MOBILE', 39.904800, 116.408000, 'DEMO-SEED', org_iam, path_iam),
            (1, 'seed-user-11', e013, d, 'IN',
             d::timestamp + time '08:47',
             'WEB', 39.905100, 116.407800, 'DEMO-SEED', org_pd, path_pd),
            (1, 'seed-user-11', e013, d, 'OUT',
             d::timestamp + time '18:22',
             'WEB', 39.905100, 116.407800, 'DEMO-SEED', org_pd, path_pd),
            (1, 'seed-user-14', e016, d, 'IN',
             d::timestamp + time '08:55',
             'GATE', 39.904000, 116.406900, 'DEMO-SEED', org_fin, path_fin),
            (1, 'seed-user-14', e016, d, 'OUT',
             d::timestamp + time '17:58',
             'GATE', 39.904000, 116.406900, 'DEMO-SEED', org_fin, path_fin);

        INSERT INTO oa_att.attendance_daily
            (tenant_id, user_id, work_date, first_in, last_out, work_minutes, status, org_id, org_path)
        VALUES
            (1, admin_sub, d,
             (d::timestamp + CASE WHEN n = 1 THEN time '09:18' ELSE time '08:52' END),
             d::timestamp + time '18:06',
             CASE WHEN n = 1 THEN 528 ELSE 554 END,
             CASE WHEN n = 1 THEN 'LATE' ELSE 'NORMAL' END,
             org_rd, path_rd)
        ON CONFLICT (tenant_id, user_id, work_date) DO UPDATE
            SET first_in = EXCLUDED.first_in,
                last_out = EXCLUDED.last_out,
                work_minutes = EXCLUDED.work_minutes,
                status = EXCLUDED.status,
                computed_at = now();
    END LOOP;

    -- ── 公文 / 知识库
    INSERT INTO oa_doc.official_doc
        (tenant_id, direction, doc_number, title, body, doc_type, urgency, secrecy,
         source_org, drafter_id, status, org_id, org_path, issued_at)
    VALUES
        (1,'OUT','DEMO-OUT-2026-001','【演示】关于国庆值班安排的通知',
         '各部门于节前报送值班表。', 'NOTICE','NORMAL','INTERNAL',
         NULL, admin_sub, 'ISSUED', org_rd, path_rd, now() - interval '3 days'),
        (1,'IN', NULL, '【演示】市经信局关于网络安全检查的函',
         '请于本月底前提交自查报告。', 'REQUEST','URGENT','INTERNAL',
         '市经济和信息化局', admin_sub, 'REVIEWING', org_rd, path_rd, NULL),
        (1,'OUT','DEMO-OUT-2026-002','【演示】三季度研发工作总结',
         '权限平台与工作台联调进展。', 'REPORT','NORMAL','PUBLIC',
         NULL, 'seed-user-1', 'ARCHIVED', org_iam, path_iam, now() - interval '20 days'),
        (1,'OUT','DEMO-OUT-2026-003','【演示】产品路线图评审纪要',
         'Q4 以工作区切换与演示数据为主。', 'NOTICE','NORMAL','INTERNAL',
         NULL, 'seed-user-11', 'ISSUED', org_pd, path_pd, now() - interval '2 days'),
        (1,'IN', NULL, '【演示】会计师事务所年审资料清单',
         '请财务部于 10 月 15 日前准备。', 'REQUEST','NORMAL','SECRET',
         '立信会计师事务所', 'seed-user-14', 'REVIEWING', org_fin, path_fin, NULL);

    INSERT INTO oa_doc.kb_folder(id, parent_id, name, path, owner_id)
    VALUES
        (kb_folder_id, 1, '入职手册', '/1/910101/', admin_sub),
        (kb_folder_fin, 1, '财务制度', '/1/910102/', 'seed-user-14')
    ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, owner_id = EXCLUDED.owner_id;

    INSERT INTO oa_doc.kb_doc(tenant_id, folder_id, title, summary, body, owner_id)
    VALUES
        (1, kb_folder_id, '【演示】新员工入职指南', '账号、工位、第一周安排',
         '入职当天到前台领取工牌，登录能力门户进入 OA。', admin_sub),
        (1, kb_folder_id, '【演示】请假与出差制度', '审批链与额度规则',
         '年假需提前 3 天申请；出差超过 3 天加一级审批。', admin_sub),
        (1, 1, '【演示】权限组周报模板', '周报结构',
         '进展 / 风险 / 下周计划。', 'seed-user-1'),
        (1, 1, '【演示】行政物资申领说明', '复印纸与文具',
         '低值易耗走物资申领，固定资产走资产领用。', 'seed-user-3'),
        (1, kb_folder_id, '【演示】产品需求模板', '背景 / 方案 / 验收',
         '每条需求必须可验收，禁止只写口号。', 'seed-user-11'),
        (1, kb_folder_fin, '【演示】差旅报销标准', '交通住宿餐补',
         '高铁一等座需总监签字；住宿上限 500 元/晚。', 'seed-user-14'),
        (1, kb_folder_fin, '【演示】备用金管理办法', '借款与核销',
         '超过 5000 元走借款单，核销不超过 30 天。', 'seed-user-14'),
        (1, 1, '【演示】会议室预定须知', '冲突由数据库排他约束拦截',
         '取消预定后时间段立即释放。', admin_sub);

    INSERT INTO oa_doc.kb_share
        (tenant_id, resource_type, resource_id, subject_type, subject_id, level, granted_by)
    SELECT 1, 'DOC', d.id, 'ALL', '*', 'READ', 'seed-demo'
      FROM oa_doc.kb_doc d
     WHERE d.title LIKE '【演示】%'
    ON CONFLICT (tenant_id, resource_type, resource_id, subject_type, subject_id) DO NOTHING;

    -- ── 行政
    INSERT INTO oa_admin.room_booking
        (tenant_id, room_id, booker_id, booker_name, subject, attendees, during, status, org_id, org_path)
    VALUES
        (1, room_a, admin_sub, '陈默', '【演示】研发周会', 12,
         tstzrange(date_trunc('day', now()) + interval '1 day 10 hour',
                   date_trunc('day', now()) + interval '1 day 11 hour', '[)'),
         'BOOKED', org_rd, path_rd),
        (1, room_b, 'seed-user-2', '王芳', '【演示】面试：前端工程师', 3,
         tstzrange(date_trunc('day', now()) + interval '1 day 14 hour',
                   date_trunc('day', now()) + interval '1 day 15 hour', '[)'),
         'BOOKED', org_hr, path_hr),
        (1, room_c, 'seed-user-11', '潘琪', '【演示】Q4 产品规划会', 28,
         tstzrange(date_trunc('day', now()) + interval '2 day 9 hour 30 minute',
                   date_trunc('day', now()) + interval '2 day 12 hour', '[)'),
         'BOOKED', org_pd, path_pd),
        (1, room_a, 'seed-user-14', '姚舟', '【演示】三季度财务对账', 6,
         tstzrange(date_trunc('day', now()) + interval '3 day 15 hour',
                   date_trunc('day', now()) + interval '3 day 17 hour', '[)'),
         'BOOKED', org_fin, path_fin);

    INSERT INTO oa_admin.asset
        (tenant_id, asset_no, name, category, brand, price, purchased_on, status, holder_id, org_id, org_path)
    VALUES
        (1,'DEMO-AST-001','MacBook Pro 14','LAPTOP','Apple', 14999, DATE '2025-03-01','IN_USE', admin_sub, org_rd, path_rd),
        (1,'DEMO-AST-002','显示器 27 寸','MONITOR','Dell', 1899, DATE '2024-11-01','IN_USE', 'seed-user-1', org_iam, path_iam),
        (1,'DEMO-AST-003','工位显示器备用','MONITOR','Dell', 1299, DATE '2023-06-01','IDLE', NULL, org_adm, path_adm),
        (1,'DEMO-AST-004','前台接待平板','TABLET','Apple', 4299, DATE '2022-01-01','REPAIR', NULL, org_adm, path_adm),
        (1,'DEMO-AST-005','ThinkPad X1','LAPTOP','Lenovo', 9899, DATE '2024-06-01','IN_USE', 'seed-user-11', org_pd, path_pd),
        (1,'DEMO-AST-006','机械键盘','PERIPHERAL','Keychron', 899, DATE '2025-01-12','IN_USE', 'seed-user-12', org_pdt, path_pdt),
        (1,'DEMO-AST-007','财务保险柜钥匙','OTHER','国产', 120, DATE '2020-01-01','IN_USE', 'seed-user-14', org_fin, path_fin),
        (1,'DEMO-AST-008','会议平板备用','TABLET','Huawei', 3599, DATE '2023-09-01','IDLE', NULL, org_adm, path_adm);

    INSERT INTO oa_admin.asset_txn(asset_id, action, actor_id, from_status, to_status, remark)
    SELECT id, 'CLAIM', holder_id, 'IDLE', 'IN_USE', '演示领用'
      FROM oa_admin.asset WHERE asset_no IN ('DEMO-AST-001','DEMO-AST-002','DEMO-AST-005','DEMO-AST-006','DEMO-AST-007');

    INSERT INTO oa_admin.supply_request(supply_id, requester_id, qty, org_id, org_path)
    VALUES
        (supply_a4, admin_sub, 2, org_rd, path_rd),
        (supply_a4, 'seed-user-3', 5, org_adm, path_adm),
        (supply_pen, 'seed-user-11', 20, org_pd, path_pd),
        (supply_a4, 'seed-user-20', 1, org_iam, path_iam);

    INSERT INTO oa_admin.vehicle_booking
        (vehicle_id, booker_id, purpose, during, status, org_id, org_path)
    VALUES
        (veh_id, admin_sub, '【演示】机场接客户',
         tstzrange(date_trunc('day', now()) + interval '2 day 8 hour',
                   date_trunc('day', now()) + interval '2 day 12 hour', '[)'),
         'BOOKED', org_rd, path_rd),
        (veh_id2, 'seed-user-4', '【演示】亦庄园区参观',
         tstzrange(date_trunc('day', now()) + interval '3 day 13 hour',
                   date_trunc('day', now()) + interval '3 day 17 hour', '[)'),
         'BOOKED', org_adm, path_adm);

    INSERT INTO oa_admin.visitor
        (tenant_id, name, company, host_id, visit_at, status, org_id, org_path)
    VALUES
        (1,'【演示】林远','演示供应商', admin_sub, now() + interval '1 day 9 hour', 'BOOKED', org_rd, path_rd),
        (1,'【演示】何倩','华东师范', 'seed-user-2', now() - interval '2 hours', 'CHECKED_IN', org_hr, path_hr),
        (1,'【演示】马哲','立信会计师事务所', 'seed-user-14', now() + interval '4 day 10 hour', 'BOOKED', org_fin, path_fin),
        (1,'【演示】顾珊','设计外包团队', 'seed-user-13', now() - interval '1 day', 'LEFT', org_pdt, path_pdt);

    -- ── 通知：收件人索引 + 站内信 + 公告
    INSERT INTO oa_notify.recipient(tenant_id, user_id)
    SELECT 1, e.user_id FROM oa_org.employee e WHERE e.emp_no LIKE 'DEMO-%'
    ON CONFLICT (tenant_id, user_id) DO NOTHING;

    INSERT INTO oa_notify.announcement
        (tenant_id, title, content, publisher_id, publisher_name, audience_type, audience_count, status)
    VALUES
        (1,'【演示】本周五下午系统维护','OA 于 18:00-20:00 停机维护，请提前提交单据。',
         admin_sub,'陈默','EXPLICIT',24,'PUBLISHED'),
        (1,'【演示】国庆放假通知','10 月 1 日至 7 日放假，值班表见公文。',
         admin_sub,'陈默','EXPLICIT',24,'PUBLISHED'),
        (1,'【演示】产品部扩编欢迎新同学','产品一组本周加入夏荷同学，请协助熟悉需求模板。',
         'seed-user-11','潘琪','EXPLICIT',24,'PUBLISHED');

    INSERT INTO oa_notify.notification
        (tenant_id, user_id, category, title, content, biz_type, biz_id, link, dedup_key)
    VALUES
        (1, admin_sub, 'TODO', '待审批：周可的年假', '家里有事，1 天', 'LEAVE', 'DEMO-LEAVE-003',
         '/workbench', 'demo:todo:leave-003'),
        (1, admin_sub, 'TODO', '待审批：李四出差', '杭州交流 2 天', 'TRAVEL', 'DEMO-TR-002',
         '/workbench', 'demo:todo:tr-002'),
        (1, admin_sub, 'ANNOUNCEMENT', '【演示】本周五下午系统维护',
         'OA 于 18:00-20:00 停机维护。', 'ANNOUNCEMENT', NULL, '/notify', 'demo:ann:maint'),
        (1, admin_sub, 'SYSTEM', '欢迎使用 OA 工作台',
         '组织、待办、知识库已灌入演示数据。', 'SYSTEM', NULL, '/workbench', 'demo:sys:welcome'),
        (1, admin_sub, 'TODO', '待审批：姚舟借款', '备用金 8000 元', 'LOAN', 'DEMO-LOAN-001',
         '/workbench', 'demo:todo:loan-001'),
        (1, admin_sub, 'TODO', '待审批：蒋川年假', '探亲 3 天', 'LEAVE', 'DEMO-LEAVE-006',
         '/workbench', 'demo:todo:leave-006'),
        (1, admin_sub, 'ANNOUNCEMENT', '【演示】产品部扩编欢迎新同学',
         '产品一组本周加入夏荷。', 'ANNOUNCEMENT', NULL, '/notify', 'demo:ann:welcome-intern'),
        (1, 'seed-user-11', 'TODO', '加班单已提交', '等待陈默审批', 'OVERTIME', 'DEMO-OT-002',
         '/workbench', 'demo:sys:ot-002'),
        (1, 'seed-user-1', 'SYSTEM', '你的出差单已提交',
         '杭州交流等待陈默审批。', 'TRAVEL', 'DEMO-TR-002', '/workbench', 'demo:sys:tr-002');

    -- ── 演示账号授权（管理员已有 SUPER_ADMIN，不重复插入）
    INSERT INTO oa_iam.grant_record
        (tenant_id, subject_type, subject_id, role_id, scope_type, grant_type, reason, granted_by, source)
    SELECT 1, 'USER', 'seed-user-2', r_hr, 'ALL', 'PERMANENT', '演示数据：人事管理员', 'seed-demo', 'MANUAL'
     WHERE NOT EXISTS (
        SELECT 1 FROM oa_iam.grant_record g
         WHERE g.subject_type='USER' AND g.subject_id='seed-user-2'
           AND g.role_id=r_hr AND g.revoked_at IS NULL);

    INSERT INTO oa_iam.grant_record
        (tenant_id, subject_type, subject_id, role_id, scope_type, scope_org_ids, include_descendants,
         grant_type, reason, granted_by, source)
    SELECT 1, 'USER', 'seed-user-3', r_mgr, 'ORG_AND_SUB', jsonb_build_array(org_adm), true,
           'PERMANENT', '演示数据：行政负责人', 'seed-demo', 'MANUAL'
     WHERE NOT EXISTS (
        SELECT 1 FROM oa_iam.grant_record g
         WHERE g.subject_type='USER' AND g.subject_id='seed-user-3'
           AND g.role_id=r_mgr AND g.revoked_at IS NULL);

    INSERT INTO oa_iam.grant_record
        (tenant_id, subject_type, subject_id, role_id, scope_type, scope_org_ids, include_descendants,
         grant_type, reason, granted_by, source)
    SELECT 1, 'USER', 'seed-user-11', r_mgr, 'ORG_AND_SUB', jsonb_build_array(org_pd), true,
           'PERMANENT', '演示数据：产品负责人', 'seed-demo', 'MANUAL'
     WHERE NOT EXISTS (
        SELECT 1 FROM oa_iam.grant_record g
         WHERE g.subject_type='USER' AND g.subject_id='seed-user-11'
           AND g.role_id=r_mgr AND g.revoked_at IS NULL);

    INSERT INTO oa_iam.grant_record
        (tenant_id, subject_type, subject_id, role_id, scope_type, scope_org_ids, include_descendants,
         grant_type, reason, granted_by, source)
    SELECT 1, 'USER', 'seed-user-14', r_mgr, 'ORG_AND_SUB', jsonb_build_array(org_fin), true,
           'PERMANENT', '演示数据：财务负责人', 'seed-demo', 'MANUAL'
     WHERE NOT EXISTS (
        SELECT 1 FROM oa_iam.grant_record g
         WHERE g.subject_type='USER' AND g.subject_id='seed-user-14'
           AND g.role_id=r_mgr AND g.revoked_at IS NULL);

    INSERT INTO oa_iam.grant_record
        (tenant_id, subject_type, subject_id, role_id, scope_type, grant_type, reason, granted_by, source)
    SELECT 1, 'USER', u, r_emp, 'SELF', 'PERMANENT', '演示数据：普通员工', 'seed-demo', 'MANUAL'
      FROM unnest(ARRAY['seed-user-1','seed-user-4','seed-user-5','seed-user-6',
                        'seed-user-7','seed-user-8','seed-user-9','seed-user-10',
                        'seed-user-12','seed-user-13','seed-user-15','seed-user-16',
                        'seed-user-17','seed-user-18','seed-user-19','seed-user-20',
                        'seed-user-21','seed-user-22']) AS u
     WHERE NOT EXISTS (
        SELECT 1 FROM oa_iam.grant_record g
         WHERE g.subject_type='USER' AND g.subject_id=u
           AND g.role_id=r_emp AND g.revoked_at IS NULL);

    INSERT INTO oa_iam.perm_user_version(user_id, version)
    SELECT e.user_id, 1 FROM oa_org.employee e WHERE e.emp_no LIKE 'DEMO-%'
    ON CONFLICT (user_id) DO UPDATE SET version = oa_iam.perm_user_version.version + 1, updated_at = now();

    UPDATE oa_iam.perm_epoch SET epoch = epoch + 1, updated_at = now() WHERE id = 1;
    UPDATE oa_iam.tenant_perm_epoch SET epoch = epoch + 1, updated_at = now() WHERE tenant_id = 1;
    UPDATE oa_org.org_tree_version SET version = version + 1, updated_at = now() WHERE id = 1;

    PERFORM setval('oa_org.org_unit_id_seq', GREATEST((SELECT max(id) FROM oa_org.org_unit), 1), true);
    PERFORM setval('oa_org.employee_id_seq', GREATEST((SELECT max(id) FROM oa_org.employee), 1), true);
    PERFORM setval('oa_org.job_position_id_seq', GREATEST((SELECT max(id) FROM oa_org.job_position), 1), true);
    PERFORM setval('oa_doc.kb_folder_id_seq', GREATEST((SELECT max(id) FROM oa_doc.kb_folder), 1), true);

    UPDATE oa_sys.id_segment SET max_id = GREATEST(max_id, 40), updated_at = now()
     WHERE biz_tag IN ('LEAVE','DOC');

    RAISE NOTICE 'demo seed bound admin_sub=% employees=% orgs=%', admin_sub,
        (SELECT count(*) FROM oa_org.employee WHERE emp_no LIKE 'DEMO-%'),
        (SELECT count(*) FROM oa_org.org_unit WHERE code LIKE 'DEMO-%');
END $$;

COMMIT;
