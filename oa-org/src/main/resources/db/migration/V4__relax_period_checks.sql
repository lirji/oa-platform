-- 放宽三处时间区间约束：> 改成 >=。
--
-- 原来的 `effective_to > effective_from` 把"同一天开、同一天关"判成非法，
-- 但这在现实里完全合法且常见：
--   · 建错了一个部门，当天就撤销；
--   · 员工上午入职、下午调岗；
--   · 汇报线配错，当天改回。
-- 语义上 [from, to) 是左闭右开区间，from == to 表示"零长度、从未生效"，
-- 这正是纠错场景需要的表达，不该被约束挡住。
-- （Phase 2 冒烟里"同日创建再撤销测试组织"就撞上了这条。）

ALTER TABLE oa_org.org_unit                DROP CONSTRAINT ck_org_period;
ALTER TABLE oa_org.org_unit                ADD  CONSTRAINT ck_org_period
      CHECK (effective_to IS NULL OR effective_to >= effective_from);

ALTER TABLE oa_org.employee_org_assignment DROP CONSTRAINT ck_assign_period;
ALTER TABLE oa_org.employee_org_assignment ADD  CONSTRAINT ck_assign_period
      CHECK (valid_to IS NULL OR valid_to >= valid_from);

ALTER TABLE oa_org.reporting_line          DROP CONSTRAINT ck_rl_period;
ALTER TABLE oa_org.reporting_line          ADD  CONSTRAINT ck_rl_period
      CHECK (valid_to IS NULL OR valid_to >= valid_from);
