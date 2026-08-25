-- 在线角色管理需要区分“直接继承关系”和供判权热路径使用的传递闭包。
-- role_inherit 继续作为闭包；本表是可编辑、可重建的权威直接边。
CREATE TABLE oa_iam.role_inherit_edge (
    tenant_id          bigint      NOT NULL,
    parent_role_id     bigint      NOT NULL REFERENCES oa_iam.role(id),
    inherited_role_id  bigint      NOT NULL REFERENCES oa_iam.role(id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, parent_role_id, inherited_role_id),
    CONSTRAINT ck_role_inherit_edge_not_self CHECK (parent_role_id <> inherited_role_id)
);

CREATE INDEX ix_role_inherit_edge_inherited
    ON oa_iam.role_inherit_edge(tenant_id, inherited_role_id);

-- V10 的 distance=1 行就是原有直接边。两端租户必须一致，避免把历史异常带进新表。
INSERT INTO oa_iam.role_inherit_edge (tenant_id, parent_role_id, inherited_role_id)
SELECT parent.tenant_id, ri.ancestor_role_id, ri.descendant_role_id
  FROM oa_iam.role_inherit ri
  JOIN oa_iam.role parent ON parent.id = ri.ancestor_role_id
  JOIN oa_iam.role inherited ON inherited.id = ri.descendant_role_id
 WHERE ri.distance = 1
   AND parent.tenant_id = inherited.tenant_id
ON CONFLICT DO NOTHING;

COMMENT ON TABLE oa_iam.role_inherit_edge IS '角色直接继承边；role_inherit 为由本表重建的传递闭包';
COMMENT ON COLUMN oa_iam.role_inherit_edge.parent_role_id IS '获得被继承角色权限的角色';
COMMENT ON COLUMN oa_iam.role_inherit_edge.inherited_role_id IS '被直接继承的角色';
