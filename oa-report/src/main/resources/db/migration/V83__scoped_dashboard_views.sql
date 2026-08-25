-- 驾驶舱专用“可过滤原子视图”。先按数据范围过滤，再聚合；不能对全公司聚合结果事后过滤。
CREATE OR REPLACE VIEW oa_sys.v_approval_efficiency_scoped AS
SELECT tenant_id, biz_type, status, outcome, submitted_at, finished_at,
       applicant_user_id, org_id, org_path
  FROM oa_flow.approval_instance;

CREATE OR REPLACE VIEW oa_sys.v_attendance_summary_scoped AS
SELECT tenant_id, user_id, work_date, status, org_id, org_path
  FROM oa_att.attendance_daily;

CREATE OR REPLACE VIEW oa_sys.v_asset_summary_scoped AS
SELECT tenant_id, category, status, price, holder_id, org_id, org_path
  FROM oa_admin.asset;

COMMENT ON VIEW oa_sys.v_approval_efficiency_scoped IS '审批效率权限原子视图，必须先按 org 范围过滤再聚合';
COMMENT ON VIEW oa_sys.v_attendance_summary_scoped IS '考勤汇总权限原子视图，必须先按 org 范围过滤再聚合';
COMMENT ON VIEW oa_sys.v_asset_summary_scoped IS '资产汇总权限原子视图，必须先按 org 范围过滤再聚合';
