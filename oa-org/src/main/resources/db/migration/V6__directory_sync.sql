-- ═══════════════════════════════════════════════════════════════════
-- 通讯录增量同步的水位线。版本号区间 V2-V9（组织域）。
--
-- 前端要做"万人通讯录 IndexedDB 增量同步"（FINAL_PLAN §8.2），
-- 需要一个【单调递增的水位线】来表达"自从 since 之后有哪些变化"。
--
-- ★ 为什么不用 updated_at：
--   ① 时钟可以回拨（NTP 校正、容器时间漂移），回拨期间的更新会被永远跳过；
--   ② 同一毫秒内的多行会在游标边界上被截断或重复；
--   ③ 批量导入时所有行时间戳相同，无法稳定分页。
--   序列不依赖时钟，天然单调、天然唯一。
--
-- ★ 为什么组织任职变更也要 bump 员工的水位线：
--   通讯录视图 join 了 employee_org_assignment —— 一个人调岗时 employee 表
--   一个字节都没变，但他在通讯录里的部门变了。只看 employee 的变更，
--   调岗永远同步不过去，本地缓存会一直显示旧部门。
-- ═══════════════════════════════════════════════════════════════════

CREATE SEQUENCE IF NOT EXISTS oa_org.directory_sync_seq;

ALTER TABLE oa_org.employee
    ADD COLUMN IF NOT EXISTS sync_seq bigint NOT NULL DEFAULT nextval('oa_org.directory_sync_seq');

CREATE INDEX IF NOT EXISTS ix_employee_sync_seq ON oa_org.employee(sync_seq);

-- 每次 UPDATE 都推进水位线。BEFORE 触发器里直接改 NEW，不产生额外的写。
CREATE OR REPLACE FUNCTION oa_org.bump_employee_sync_seq() RETURNS trigger AS $$
BEGIN
    NEW.sync_seq := nextval('oa_org.directory_sync_seq');
    RETURN NEW;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_employee_sync_seq ON oa_org.employee;
CREATE TRIGGER trg_employee_sync_seq
    BEFORE INSERT OR UPDATE ON oa_org.employee
    FOR EACH ROW EXECUTE FUNCTION oa_org.bump_employee_sync_seq();

-- 任职变更（入职/调岗/兼岗/离职）连带推进对应员工的水位线。
-- 这个触发器会触发上面那个 BEFORE UPDATE —— 那只是给 NEW 赋个值，不会递归。
CREATE OR REPLACE FUNCTION oa_org.bump_employee_by_assignment() RETURNS trigger AS $$
DECLARE
    eid bigint := COALESCE(NEW.employee_id, OLD.employee_id);
BEGIN
    UPDATE oa_org.employee SET sync_seq = nextval('oa_org.directory_sync_seq') WHERE id = eid;
    RETURN NULL;   -- AFTER 触发器，返回值被忽略
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_assignment_sync_seq ON oa_org.employee_org_assignment;
CREATE TRIGGER trg_assignment_sync_seq
    AFTER INSERT OR UPDATE OR DELETE ON oa_org.employee_org_assignment
    FOR EACH ROW EXECUTE FUNCTION oa_org.bump_employee_by_assignment();

-- 把水位线暴露进通讯录视图。
-- ★ 严格保持原有语义：INNER JOIN（没有主岗的人不进通讯录）+ 排除 LEFT（离职的不出现）。
--   只加一列 sync_seq。改这两处会让通讯录悄悄多出/少掉人，是最不该在"加个字段"时发生的事。
DROP VIEW IF EXISTS oa_org.v_employee_directory;
CREATE VIEW oa_org.v_employee_directory AS
 SELECT e.id AS employee_id,
    e.user_id,
    e.emp_no,
    e.name,
    e.en_name,
    e.email,
    e.mobile_enc,
    e.status,
    e.hire_date,
    e.sync_seq,
    a.assignment_type,
    a.is_leader,
    o.id AS org_id,
    o.path AS org_path,
    o.name AS org_name,
    o.depth AS org_depth,
    p.name AS position_name
   FROM oa_org.employee e
     JOIN oa_org.employee_org_assignment a ON a.employee_id = e.id AND a.valid_to IS NULL
          AND a.assignment_type::text = 'PRIMARY'::text
     JOIN oa_org.org_unit o ON o.id = a.org_unit_id
     LEFT JOIN oa_org.job_position p ON p.id = a.position_id
  WHERE e.status::text <> 'LEFT'::text;

-- ───────────────────────────────── 墓碑视图
-- 增量同步只发"变了的行"是不够的：一个人离职后，他的行从 v_employee_directory 里
-- 【消失】而不是【变化】—— 客户端收不到任何消息，本地缓存里会永远躺着已离职的人。
-- 这是增量同步最经典的一个 bug，而且它只在"某人离职"这种低频事件后才显形。
--
-- 所以墓碑必须单独查：直接读 employee（不经视图），凡是 LEFT 或已无有效主岗的，
-- 都作为"应从本地删除"下发。
CREATE OR REPLACE VIEW oa_org.v_directory_tombstone AS
SELECT e.id AS employee_id, e.user_id, e.sync_seq
  FROM oa_org.employee e
 WHERE e.status = 'LEFT'
    OR NOT EXISTS (SELECT 1 FROM oa_org.employee_org_assignment a
                    WHERE a.employee_id = e.id AND a.valid_to IS NULL
                      AND a.assignment_type = 'PRIMARY');
