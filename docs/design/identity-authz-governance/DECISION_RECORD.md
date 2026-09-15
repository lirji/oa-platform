# DECISION_RECORD · identity-authz-governance

本文件只记录**本次升级**的决策。历史 ADR-0001.. 仍有效。

| ID | 决策 | 否决 | 依据 |
|---|---|---|---|
| D-GOV-001 | 主库继续 PostgreSQL 16 | 本期迁 MySQL | F2；org_path/pg_trgm/分区；提示词「无充分理由不换栈」 |
| D-GOV-002 | 治理上下文放在 `oa-iam` 模块，不拆新进程 | identity-service 等独立部署 | ADR-0002；热路径必须进程内 |
| D-GOV-003 | 不引入 Nacos / Apollo / SCG / OpenFeign | 按硬约束立即上云原生全家桶 | 无对应故障；nginx+env 已够；硬约束 §5 禁止双配置中心 |
| D-GOV-004 | Casdoor 只认证；OA DecisionEngine 做授权 | 登录即授权；SpiceDB 主 PDP | F4/F11；ADR-0001 |
| D-GOV-005 | Identity 叠加投影；Human grant 仍用 Casdoor sub | 把 grant.subject_id 一次性改成 identity UUID | 保护 PermissionEngine 热路径 |
| D-GOV-006 | 图谱用关系边表 | Neo4j | 一期 1–2 跳 |
| D-GOV-007 | S02 用合成 policyId；独立 Policy 聚合后置 | 先做完整 Policy Store 再 Check | 最小可运行平台 |
| D-GOV-008 | Check 写决策审计；`@RequiresPerm` 热路径不写 | 每次 HTTP 判权同步插审计表 | 保护微秒热路径 |
| D-GOV-009 | 新增 SQL 用 Mapper XML；不回刷旧注解 Mapper | 全仓改 XML | 债务隔离 |
| D-GOV-010 | Access Request 复用现有审批能力，不新写 BPM | 自研第二套流程引擎 | 提示词 §十六 |
| D-GOV-011 | Agent 不继承 Owner 权限 | Agent=User 或 Owner 全权 | 硬约束 §17 |
| D-GOV-012 | Kafka 沿用；S01 不发事件 | 新 MQ 或所有写都 Outbox | 硬约束 §13 Complexity Budget |
| D-GOV-013 | IAM 新 Flyway 从 **V110** 起 | 改 V10–V19 | 版本区间已满；out-of-order 已开 |
| D-GOV-014 | 待办委托与权限委托分离 | 复用 delegation 表塞权限 | F7；避免语义污染 |
| D-GOV-015 | 治理 UI 先做 `/iam` Tab + 详情路由 | 新顶层菜单信息架构 | 首屏 300KB；菜单仍后端下发 |

Assumption 被推翻时：A3（投影模型）会影响 D-GOV-005 与所有 grant 切片，必须回溯快照构建。
