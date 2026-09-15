# BRIEF · 身份与授权治理平台（Brownfield）

- Artifact: `BRIEF`
- Owner: `public-engineering-workflow`
- Slug: `identity-authz-governance`
- Mode: **Brownfield**
- Date: 2026-09-15

## 问题与角色

现有仓库是一套可运行的万人级 OA（组织 / RBAC+ABAC / 审批 / 考勤 / 公文）。
本次要把**核心领域**从「OA 审批协同」重新聚焦为：

> 身份 → 权限数据 → 策略 → 决策 → 授权 → 审计 → 风险 → 生命周期

目标产品定位：**企业级身份与授权治理平台**。
现有 OA 审批保留为 Access Request / 委托 / 提权 / 高风险授权的工作流底座，不再作为整个系统的核心子域。

**主角色**

| 角色 | 主作业 |
|---|---|
| 权限管理员 | 管理身份、角色、策略、授权、回收、沙盘解释 |
| 身份管理员 | 管理 Human / NHI / Agent 生命周期与标签 |
| 审批人 | 批准权限申请、JIT 提权、委托、高风险授权 |
| 审计 / 风控 | 查决策链、风险发现、授权来源 |
| 普通员工 | 申请权限、委托待办、查看自己的有效权限 |
| Agent / 服务账号 | 以独立 Principal 调用 Tool/API，不继承 Owner 全权 |

## 主作业（必须能回答的 12 个问题）

1. Who are you?
2. What are you allowed to access?
3. Why are you allowed to access it?
4. Which policy produced this decision?
5. Who granted this permission?
6. When will this permission expire?
7. What permissions does this identity effectively have?
8. Who can access this resource?
9. Is this permission risky?
10. Can this Agent call this Tool/API/Resource?
11. What happened during this authorization decision?
12. Can the complete authorization chain be audited?

## 非目标

- 不把现有 OA 业务（请假、打卡、会议室、知识库）推倒重写。
- 不把类/表改名伪装成新能力。
- 不把当前模块化单体一次性拆成 `identity-service` / `authorization-service` 等独立进程。
- 不把 PostgreSQL 迁到 MySQL（见 TECH_SELECTION；这是独立迁移程序）。
- 不引入 Nacos / Apollo / Spring Cloud Gateway / OpenFeign（见 Complexity Budget）。
- 不引入图数据库、独立规则引擎、OPA/SpiceDB 作为主 PDP。
- 不在本期连接真实 AD/LDAP/HR；只抽象 Connector。
- 不实现自定义密码学；Secret 不入日志/Git/业务表明文。
- 不做 git push / PR / Release / 生产 Deploy（无独立授权）。
- 不自动本地 commit。

## 用户约束

来源：

- `/Users/liruijun/Downloads/身份授权平台架构硬约束.md`
- `/Users/liruijun/Downloads/OA项目升级为身份授权治理平台提示词.md`

硬约束中与仓库事实冲突的项，按「兼容方案 + 演进方案」处理，不一次性换栈。
优先级：用户明确要求 > 仓库可验证事实 > 已批准 ADR > Assumption。

工程约束沿用本仓库：

- JDK 21 / Spring Boot 3.x
- `@RequiresPerm` 或 `@PublicApi`；权限点必须进 IAM 迁移
- 跨模块只依赖 `..api..`
- 业务表 `org_id` + `org_path`（`text_pattern_ops`）
- 数据权限 `org_path LIKE '前缀%'`，算不出则 `1=0`
- 主体标识 Casdoor `sub`（UUID 字符串）
- 新增 SQL：Mapper 接口 + Mapper XML；禁止注解 SQL 与 Java 拼 SQL
- 表/字段中文 COMMENT；新注释中文
- API Golden 冻结 HTTP 方法 + 路径 + 权限点
- 缓存加载 get-then-put，禁止 `Caffeine.get(key, loader)` pin 虚拟线程

## Greenfield / Brownfield

**Brownfield。** 默认兼容现有部署拓扑、PostgreSQL、Casdoor、Kafka、Redis、模块化单体 + 3 独立服务。
历史模块划分不等于目标领域划分：在 `oa-iam` 内用限界上下文演进，而不是按表拆服务。

## Facts（仓库已验证）

- F1: JDK 21，Spring Boot 3.3.5，无 Spring Cloud / Nacos / Apollo / OpenFeign / SCG。
- F2: 主库 PostgreSQL 16，同库分 schema；Flyway `out-of-order`；IAM 版本 V10–V19 已用完。
- F3: 可部署单元：`oa-app:8400` + notify/file/job + console/mobile nginx 反代。
- F4: 认证 = Casdoor JWT；授权 = 本地 `PermissionEngine`（L1 Caffeine + L2 Redis + DB 重算）。
- F5: 已有 RBAC1（角色继承闭包）、ABAC（受限 SpEL）、数据权限、字段脱敏、JIT 四眼提权。
- F6: `grant_record` 主体仅 `USER|ORG_UNIT|POSITION|USER_GROUP`；无 NHI/Agent。
- F7: `delegation` 表只做**待办代理**，不叠加 RBAC；`grant_type=DELEGATED` 仅预留未写入。
- F8: 离职会撤销该 USER 的全部 `grant_record` 并 bump 租户 epoch。
- F9: 无 Identity Source / Credential / RiskFinding / 决策审计专用表 / Check API。
- F10: ADR-0002 已否决完整微服务（Nacos+Gateway+DB per service）。
- F11: SpiceDB 仅知识库可选骨架，默认关闭且 fail-fast。
- F12: 现有 Mapper SQL 几乎全是注解 SQL；本仓库硬约束要求**新增**走 XML。
- F13: 审计为 `oa_sys.audit_log` 的 HTTP 写审计（含 DENIED），不是 PDP 决策审计。

## Assumptions

- A1: 本期租户仍为租户 1；不引入真实多租户 RLS。
- A2: Casdoor 继续只做 Authentication；OA 继续做 Authorization。
- A3: Identity 采用**投影叠加**：Human 的 `grant_record.subject_id` 仍是 Casdoor sub，不改热路径主键。
- A4: 身份图谱一期用关系表 1–2 跳查询，不引入图数据库。
- A5: 外部身份源一期只提供 INTERNAL + MOCK Connector。
- A6: oa-mobile 本期不作为治理控制台；治理 UI 只进 `oa-console`。
- A7: OA 业务模块继续作为授权的**资源消费方**，不被重写成 IAM 核心。
- A8: 硬约束中的 MySQL / Nacos / Apollo / SCG / Feign 是**目标演进选项**，不是本期必上组件。

## 已知 NFR / 规模

- 现网设计口径：约 1 万人、约 3000 组织节点。
- 判权热路径必须继续零 DB / 零远程；现有 P99 量级是微秒级内存判定。
- 新增 Check API 对 Human+权限点 必须复用 `PermissionEngine` 内存快照。
- Agent / NHI 的 Check 允许走独立读模型，但不能把 Human 热路径拖进 DB。
- 不编造新的 TP99。

## 风险

- R1: 改 `grant_record` 主体类型或快照构建，可能破坏已验证的权限热路径。
- R2: 身份投影与员工主数据漂移（入职未建身份 / 离职身份仍 ACTIVE）。
- R3: 新增接口未同步 Golden / 权限目录 / `pnpm gen:perm` 会导致构建失败。
- R4: 决策审计全量写入可能拖慢热路径；必须异步或仅 Check API 同步落库。
- R5: 为「企业级」堆中间件（Nacos/Apollo/网关/图库）会扩大故障面且与 ADR-0002 冲突。

## 会改变架构的未决问题

无关键 HOLD。下列已用 Assumption/Decision 消化：

- PostgreSQL vs 硬约束 MySQL → D-GOV-001
- 微服务拆分 vs 模块化单体 → D-GOV-002
- Nacos/Apollo/SCG → D-GOV-003

## 停止点与授权

- 设计 Gate 非 HOLD 后**直接实施**纵向切片，不等用户逐步确认。
- Git：无 `git_commit` / `git_push` / `create_pr` / `release` / `deploy` grant。
- 验证完成后更新进度，停在已验证状态。
