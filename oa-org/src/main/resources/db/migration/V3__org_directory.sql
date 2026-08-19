-- 通讯录读模型（视图）。
--
-- 为什么用视图而不是宽表：ADR-0007 要求「数据权限的过滤列必须在同一张表上」，
-- 否则 @DataScope 的 alias 只能命中一张表，org_path / user_id / org_id 分散在三张表就没法一起过滤。
-- 视图把它们并到一处，且天然与源表一致，不需要任何投影维护逻辑。
-- 等 Phase 3 通讯录要做 IndexedDB 增量同步时，再按需换成带 version 列的物化宽表。
CREATE VIEW oa_org.v_employee_directory AS
SELECT e.id            AS employee_id,
       e.user_id       AS user_id,
       e.emp_no        AS emp_no,
       e.name          AS name,
       e.en_name       AS en_name,
       e.email         AS email,
       e.mobile_enc    AS mobile_enc,
       e.status        AS status,
       e.hire_date     AS hire_date,
       a.assignment_type AS assignment_type,
       a.is_leader     AS is_leader,
       o.id            AS org_id,
       o.path          AS org_path,     -- ★ 数据权限前缀匹配就打在这一列上
       o.name          AS org_name,
       o.depth         AS org_depth,
       p.name          AS position_name
  FROM oa_org.employee e
  JOIN oa_org.employee_org_assignment a
       ON a.employee_id = e.id AND a.valid_to IS NULL AND a.assignment_type = 'PRIMARY'
  JOIN oa_org.org_unit o ON o.id = a.org_unit_id
  LEFT JOIN oa_org.job_position p ON p.id = a.position_id
 WHERE e.status <> 'LEFT';

COMMENT ON VIEW oa_org.v_employee_directory IS '通讯录读模型；org_path 列供 @DataScope 前缀过滤';
