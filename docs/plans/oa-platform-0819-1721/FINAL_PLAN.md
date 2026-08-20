# OA Platform 最终实施计划（FINAL_PLAN）

> 版本 v1.0 · 2026-08-19 · 项目 **本仓库** · groupId `com.lrj.oa`
> 决策依据见同目录 `DECISION_RECORD.md`（ADR-0001 ~ 0011）。
> 技术基线：Spring Boot 3.3.5 + JDK 21（虚拟线程）+ PostgreSQL 16 + Redis + Kafka，沿用 [[local-dev-env]] 的 JDK21/system-maven 惯例。

---

## 1. 背景与定位

企业级 OA 协同办公平台，服务 **10,000 员工**规模企业。核心难点不在业务模块数量，而在两处：

1. **组织与权限的建模深度** —— 团队/部门/组的任意层级、一人多岗、汇报线、部门权限继承、临时授权/委托代理，
   以及页面/接口/数据三类权限的一致落地；
2. **万人级的性能与并发** —— 判权在每个请求的热路径上，组织范围计算在每条 SQL 上，
   打卡有早高峰尖峰，通知有全员广播。

本平台不重复造轮子：**认证复用 Casdoor**（auth-platform 已在跑 :8000），
**审批编排复用 Flowable 中台**（workflow-platform :8300 已跑通 shadow 全栈 e2e），
OA 专注做好**组织域 + 权限域 + 协同业务**。

---

## 2. 目标与非目标

### 目标

- G1 **统一组织模型**：任意层级（集团/公司/事业部/中心/部门/团队/组/虚拟组）、一人多岗（主岗/兼岗/虚线）、
  独立汇报线、**生效日期（拉链）** 支持历史还原。
- G2 **完整权限体系**：RBAC1（角色继承）+ 组织作用域 + 部门继承 + ABAC 条件 + **临时授权三形态**
  （定时角色 / JIT 提权 / 委托代理），覆盖**页面 / 接口 / 数据（行级+列级）** 三类。
- G3 **热路径零 DB 零远程**：判权 P99 < 1ms；改组织/改角色/临时授权后**秒级生效**（含前端不刷新即生效）。
- G4 **万人级容量达标**：打卡 500 QPS 持续 5min 零错误零重复；全员公告广播 10,000 推送；工作台首屏 P99 < 200ms。
- G5 **全模块规划到位**：18 个业务模块的领域模型 + 接口契约一次写清，实施分 Phase 0–8。
- G6 **双端交付**：PC 管控台 + 独立移动端 H5（打卡/审批为主）。
- G7 **可审计可回滚**：权限变更、临时提权、数据导出强制审计；每个 Phase 有开关与回滚路径。

### 非目标（明确不做，防止范围蔓延）

- N1 **不做分库分表**（ADR-0011）。
- N2 **不做完整微服务拆分**（ADR-0002），只独立通知/文件/定时三个服务。
- N3 **不做真多租户隔离**（表预留 `tenant_id`，本轮单租户；不上 PG RLS）。
- N4 **不把组织/RBAC 塞进 SpiceDB**（ADR-0001），仅知识库子域可选挂载。
- N5 **不做人事薪酬核算 / 不做财务总账**（报销单只到"审批通过 + 导出付款文件"，不接银行）。
- N6 **不做即时通讯 IM**（只做站内信 + 推送，不做聊天）。
- N7 首轮**不接**真实企微/钉钉/飞书生产租户（预留适配层 + mock）。

---

## 3. 核心领域模型

### 3.1 组织域（`oa-org`）

#### 3.1.1 组织单元 —— 单表 + type + 闭包 + 路径（ADR-0006）

```sql
org_unit
  id            bigserial pk
  tenant_id     bigint      not null default 1
  parent_id     bigint      null            -- 根节点为 null
  code          varchar(64) not null        -- 业务编码, unique(tenant_id, code)
  name          varchar(128) not null
  short_name    varchar(64)
  type          varchar(16) not null        -- GROUP|COMPANY|BU|CENTER|DEPT|TEAM|SQUAD|VIRTUAL
  path          varchar(512) not null       -- 物化路径 '/1/23/456/'
  depth         smallint    not null
  sort_order    int         not null default 0
  leader_user_id        varchar(64)         -- 负责人(Casdoor sub)
  deputy_leader_user_id varchar(64)         -- 副手
  cost_center   varchar(64)
  status        varchar(16) not null        -- ACTIVE|FROZEN|DISSOLVED
  effective_from date not null default current_date
  effective_to   date                       -- null = 至今
  version        int not null default 0     -- 乐观锁
  remark, created_by, created_at, updated_by, updated_at

org_closure                                  -- 祖先/后代 O(1)
  ancestor_id  bigint not null
  descendant_id bigint not null
  distance     smallint not null            -- 自身 distance=0
  pk(ancestor_id, descendant_id)
```

**索引**（漏一个就是性能事故）：
```sql
CREATE UNIQUE INDEX uk_org_code   ON org_unit(tenant_id, code);
CREATE        INDEX ix_org_parent ON org_unit(parent_id);
CREATE        INDEX ix_org_path   ON org_unit(path text_pattern_ops);  -- ★ 前缀 LIKE 必须
CREATE        INDEX ix_closure_desc ON org_closure(descendant_id, ancestor_id);
```

**组织调整语义**：
- **新增**：插入 org_unit + 从父的 closure 派生本节点行（`INSERT ... SELECT ancestor_id, new_id, distance+1 FROM org_closure WHERE descendant_id = parent`）。
- **移动子树**：事务内 ① 删除"子树 × 旧祖先"的 closure 行；② 插入"子树 × 新祖先"；③ 批量更新子树 `path`/`depth`。
  万人级下最大子树约数百节点，closure 行数 ≈ 节点数 × 深度 ≈ 数千行，单事务可完成。
- **完成后**发 `OrgTreeChangedEvent` → `OrgTreeCache` 重建 + Redis 广播 + bump 全局权限 epoch。
- **不回刷业务表的 org_path**（ADR-0007，历史单据保留当时归属）。

#### 3.1.2 人 / 岗 / 任职 —— 人岗分离

```sql
employee
  id, tenant_id,
  user_id     varchar(64) not null unique,   -- ★ Casdoor `sub`(UUID)，全系统主体标识
  emp_no      varchar(32) not null unique,   -- 工号
  name, en_name, avatar,
  mobile_enc  bytea,  mobile_hash varchar(64),  -- AES-GCM 加密 + 确定性哈希(供精确查)
  email, id_card_enc bytea, id_card_hash varchar(64),
  gender, birthday,
  hire_date, regular_date, leave_date,
  employment_type varchar(16),               -- FULL_TIME|INTERN|OUTSOURCE|CONSULTANT
  status       varchar(16) not null,         -- PROBATION|ACTIVE|LEAVING|LEFT
  ext_json     jsonb, version, 审计列...

position                                      -- 岗位/职位
  id, tenant_id, code unique, name,
  job_family varchar(32),                     -- 职族 研发/产品/销售/职能
  job_level  varchar(16),                     -- 职级 P5/M3
  is_manager boolean not null default false,
  status

employee_org_assignment                       -- ★ 一人多岗的核心（拉链表）
  id, employee_id, org_unit_id, position_id,
  assignment_type varchar(16) not null,       -- PRIMARY(主岗) | CONCURRENT(兼岗) | DOTTED(虚线)
  is_leader boolean not null default false,   -- 是否该组织负责人
  valid_from date not null, valid_to date,    -- null = 至今
  created_by, created_at

  -- 同一人在同一组织同一岗位不能有重叠有效期
  UNIQUE (employee_id, org_unit_id, position_id, valid_from)
  -- 主岗唯一：一人同一时刻只能有一个 PRIMARY
  CREATE UNIQUE INDEX uk_primary_assignment ON employee_org_assignment(employee_id)
    WHERE assignment_type='PRIMARY' AND valid_to IS NULL;
  CREATE INDEX ix_assign_emp_active ON employee_org_assignment(employee_id) WHERE valid_to IS NULL;
  CREATE INDEX ix_assign_org_active ON employee_org_assignment(org_unit_id) WHERE valid_to IS NULL;

reporting_line                                -- ★ 汇报线独立于组织树
  id, employee_id, manager_employee_id,
  type varchar(8) not null,                   -- SOLID(实线) | DOTTED(虚线)
  valid_from date not null, valid_to date
  CREATE UNIQUE INDEX uk_solid_manager ON reporting_line(employee_id)
    WHERE type='SOLID' AND valid_to IS NULL;
```

**为什么汇报线要独立**：审批"逐级上报"经常≠组织树（跨部门项目经理、矩阵式管理下的虚线上级）。
把它硬绑在组织树上，第一个矩阵式组织的请假审批就会走错人。

#### 3.1.3 组织域对外契约（`OrgQueryApi`，跨模块唯一入口）

```java
public interface OrgQueryApi {
    OrgUnitView          getOrg(Long orgId);
    List<Long>           descendantIds(Long orgId);          // 含自身，内存 O(1)
    List<Long>           ancestorIds(Long orgId);            // 含自身
    String               pathOf(Long orgId);                 // '/1/23/456/'
    boolean              isAncestor(Long a, Long b);
    EmployeeView         getEmployeeByUserId(String userId);
    List<AssignmentView> activeAssignments(String userId);   // 主岗+兼岗
    Long                 primaryOrgId(String userId);
    Optional<String>     directManagerUserId(String userId); // SOLID 汇报线
    List<String>         managerChain(String userId, int maxLevel); // 逐级上报链
    List<String>         orgLeaderUserIds(Long orgId, boolean bubbleUpIfEmpty); // 部门负责人，空则上卷
    OrgSnapshotView      asOf(String userId, LocalDate date);// 历史还原
}
```

---

### 3.2 权限域（`oa-iam`）

#### 3.2.1 权限点 / 角色 / 角色继承

```sql
permission
  id, code varchar(128) unique,       -- 'oa:leave:approve' / 'oa:menu:attendance' / 'oa:field:salary'
  name, type varchar(8) not null,     -- MENU | BUTTON | API | DATA | FIELD
  module varchar(32),                 -- org|iam|flow|attendance|doc|admin|report
  parent_id bigint,                   -- MENU 类型构成菜单树
  resource varchar(256), method varchar(8),  -- API 类型：兜底 URL 匹配用
  icon, route, sort_order, status, builtin boolean

role
  id, tenant_id, code unique, name,
  type varchar(16),                   -- SYSTEM|BUSINESS|CUSTOM
  default_scope varchar(24),          -- ALL|ORG_AND_SUB|ORG|SELF|CUSTOM
  status, builtin boolean, version

role_inherit                          -- RBAC1 角色继承（闭包，避免递归查询）
  ancestor_role_id, descendant_role_id, distance
  pk(ancestor_role_id, descendant_role_id)

role_permission (role_id, permission_id)  pk 复合
```

#### 3.2.2 授权表 `grant` —— 全部权限语义的收敛点 ★

```sql
grant_record                          -- 表名避开 SQL 关键字 GRANT
  id            bigserial pk
  tenant_id     bigint not null
  subject_type  varchar(16) not null  -- USER | POSITION | ORG_UNIT | USER_GROUP
  subject_id    varchar(64) not null  -- userId / positionId / orgUnitId / groupId
  role_id       bigint not null
  scope_type    varchar(24) not null  -- ALL|ORG_AND_SUB|ORG|SELF|CUSTOM
  scope_org_ids jsonb                 -- CUSTOM 时的组织集合
  include_descendants boolean not null default true   -- ★ subject=ORG_UNIT 时是否含子部门
  grant_type    varchar(16) not null  -- PERMANENT | TEMPORARY | DELEGATED
  valid_from    timestamptz not null default now()
  valid_to      timestamptz           -- null = 永久；★ 临时授权靠它
  source        varchar(16) not null  -- MANUAL|RULE|SYNC|APPROVAL
  approval_instance_id varchar(64)    -- JIT 提权对应的审批单
  reason        text
  granted_by    varchar(64), granted_at timestamptz
  revoked_at    timestamptz, revoked_by varchar(64), revoke_reason text

  CREATE INDEX ix_grant_subject ON grant_record(subject_type, subject_id)
    WHERE revoked_at IS NULL;
  CREATE INDEX ix_grant_expiring ON grant_record(valid_to)
    WHERE valid_to IS NOT NULL AND revoked_at IS NULL;   -- 到期回收任务扫这个
```

**"部门权限继承"必须区分的三层含义**（很多实现混为一谈，这里显式建模）：

| 含义 | 方向 | 表达方式 |
|---|---|---|
| ① **授权对象继承**：授权给部门 A → A 及子部门全员获得该角色 | 向下 | `subject_type=ORG_UNIT` + `include_descendants=true` |
| ② **数据范围继承**：某人能看到本部门 + 所有子部门的数据 | 向下 | `scope_type=ORG_AND_SUB` → SQL `org_path LIKE '/1/23/%'` |
| ③ **管理权上卷**：上级部门负责人自动能管下级 | 向上 | 无需额外机制——给 leader 角色配 `ORG_AND_SUB` scope 即自然成立 |

#### 3.2.3 临时授权三形态（用户明确点名）

**① 定时角色（Temporary Role）** —— 最轻量
- 就是 `grant_record` 带 `valid_from/valid_to`，`grant_type=TEMPORARY`。
- **判定时按时间窗过滤**，不依赖定时任务删除（定时任务只做归档 + 到期提醒 + 审计）。
- 到期前 24h 通过 `oa-notify-service` 提醒授权人续期。

**② JIT 提权（Just-In-Time Elevation）** —— 高危权限专用
```
申请(填理由+时长, 上限8h) → workflow 审批(BPMN oa-elevation-v1)
  → 通过则写 grant_record(TEMPORARY, source=APPROVAL, approval_instance_id=...)
  → 提权期间所有操作 audit_log 打 elevated=true 标记
  → 到期 oa-job-service 回收 + 生成"提权期间做了什么"报表
```
- 高危权限点在 `permission` 上打 `require_elevation=true` 标（如"导出全员薪资"、"删除组织"）。
- 即便有 PERMANENT grant，命中 `require_elevation` 的权限点也必须有活跃 JIT 提权记录才放行。

**③ 委托代理（Delegation）** —— OA 高频刚需，与提权是**不同的东西**
```sql
delegation
  id, delegator_user_id, delegatee_user_id,
  scope varchar(16),          -- ALL_TODO | BY_PROCESS_KEY | BY_ROLE
  process_keys jsonb, role_ids jsonb,
  valid_from, valid_to, reason,
  status varchar(16),         -- ACTIVE|EXPIRED|REVOKED
  created_at, created_by
```
- 代理人**以委托人名义**办事：审批动作落 `on_behalf_of=delegator_user_id`，
  流程轨迹显示"张三（李四代）"，委托人回来能看到代理期间发生的一切。
- 代理**不叠加权限**：代理人只能办"委托人本来能办的那些待办"，不获得委托人的其它权限
  （区别于 ①②，这是防越权的关键约束）。
- 实现：`todo_item` 查询时 `assignee IN (me) OR assignee IN (我代理的人)`；
  办理时校验 delegation 有效 + 写 on_behalf_of。

#### 3.2.4 ABAC 条件（可选增强）

```sql
permission_condition
  id, role_id, permission_id,
  expression varchar(512),    -- SpEL: "#amount <= 5000 and #dept == #user.deptId"
  description
```
用于"报销 5000 元以下才可审批"这类金额/属性约束。判定在 `@RequiresPerm` 切面里对方法参数求值。
**默认不启用**（`oa.iam.abac.enabled=false`），Phase 2 建表 + 引擎，Phase 4 报销单场景启用。

---

## 4. 判权引擎与缓存（性能核心，ADR-0005）

### 4.1 PermissionSnapshot

```java
public record PermissionSnapshot(
    String userId,
    long   globalEpoch,                    // 与 Redis oa:perm:epoch 比对
    long   userVersion,                    // 与 Redis oa:perm:ver:{userId} 比对
    RoaringBitmap permBits,                // permission.id 位图（MENU/BUTTON/API/FIELD 全在内）
    Set<String>   permCodes,               // 调试/下发前端用；判定走 bits
    DataScope     mergedScope,             // 合并后最宽范围
    Map<String, DataScope> moduleScope,    // 按模块细分：考勤=全公司、报销=本部门
    List<String>  scopePathPrefixes,       // ['/1/23/', '/1/45/']  ★ 数据权限主用
    Set<Long>     scopeOrgIds,             // 小集合时的精确 id
    Set<Long>     elevatedPermIds,         // 当前活跃 JIT 提权覆盖的权限点
    Set<String>   delegatorUserIds,        // 我正在代理谁
    long          builtAt, expireAt
) {}
```
> 容量估算：800 个权限点的 RoaringBitmap ≈ **< 1 KB**；路径前缀通常 1–5 条。
> 单快照约 2–4 KB，10,000 用户全驻内存也只有 ~30 MB —— L1 完全放得下。

### 4.2 三级缓存与失效

| 层 | 介质 | Key | TTL | 命中成本 |
|---|---|---|---|---|
| L1 | Caffeine（进程内） | `userId` | 5 min，maxSize 20,000 | 纳秒级 |
| L2 | Redis | `oa:perm:snap:{userId}` | 30 min，Protobuf/Kryo 序列化（**禁 JDK 序列化**） | ~1 ms |
| L3 | PostgreSQL 重算 | — | — | ~10–30 ms |

**失效策略（混合，兼顾正确性与开销）**：

| 变更 | 影响面 | 动作 |
|---|---|---|
| 改 `permission` / `role_permission` / `role_inherit` | 可能全员 | `INCR oa:perm:epoch` → 所有快照失效（低频，一天几次） |
| 改 `grant_record`（subject=USER） | 1 人 | `INCR oa:perm:ver:{userId}` |
| 改 `grant_record`（subject=ORG_UNIT / POSITION / GROUP） | 几十~几千人 | `INCR oa:perm:epoch`（部门级授权本身低频，比逐个 bump 几千 key 更可靠） |
| 组织树变更（移动/新增/停用） | 范围计算全变 | `INCR oa:perm:epoch` + `OrgTreeCache` 重建 |
| JIT 提权生效/到期 | 1 人 | `INCR oa:perm:ver:{userId}` + 推送前端 |

**跨节点广播**：Redis Pub/Sub channel `oa:perm:invalidate`（payload 为 `{type, userId?}`），
各节点收到清对应 L1 条目。缓存失效消息可丢（最坏等 TTL），故不用 Kafka。

**读取时的版本校验**（防止"缓存里是旧快照但没收到广播"）：
L1 命中后比对 `globalEpoch`（本地缓存 epoch 值，1s 刷新一次）与 `userVersion`，不一致则回源。
1 秒的 epoch 刷新窗口是"改权生效延迟"的上界。

### 4.3 组织树常驻内存

```java
@Component
public class OrgTreeCache {
    private volatile OrgTreeSnapshot current;   // immutable，COW 整体替换，无锁读

    // 万人级：org_unit ≈ 3,000 节点，含 closure 展开约 20,000 条边 → 几 MB
    // 提供 O(1)：ancestorsOf / descendantsOf / pathOf / isAncestor / leadersOf
    @EventListener public void on(OrgTreeChangedEvent e) { rebuild(); }
    @Scheduled(fixedDelay = 300_000) public void safetyRebuild() { rebuildIfVersionChanged(); }
}
```
> 这是最大的性能杠杆：把"求某人可见的全部子部门"从递归 SQL 变成内存遍历，
> 消灭约 90% 的权限相关 DB 查询。

### 4.4 判权热路径

```
HTTP 请求
 → JwtAuthFilter：本地 JWK 校验 Casdoor 签名（JWKS 缓存 1h，不走网络）
 → UserContextHolder 装配 { userId(sub), employeeId, primaryOrgId }
 → PermissionEngine.get(userId)：L1 → (L2) → (L3)
 → snapshot.permBits.contains(permId)  ← O(1) 位图查
 → 通过 / 403
```
**热路径零 DB、零远程调用**。目标 **P99 < 1 ms**（不含网络）。

### 4.5 影子校验（正确性兜底）

`oa.iam.shadow-verify.enabled=true` 时，按采样率（默认 1%）对判权结果同时走 L3 DB 重算并比对，
不一致则 `WARN` + 打点 `oa_perm_mismatch_total`。Phase 2 上线后跑 2 周，指标为 0 再关。
**"改权不生效"是安全事故，必须有可观测的兜底。**

---

## 5. 三类权限的落地（ADR-0008）

### 5.1 接口权限（API）—— 唯一安全边界

```java
@RequiresPerm("oa:leave:approve")
@RequiresPerm(value = {"oa:org:edit", "oa:org:admin"}, logical = OR)
@RequiresPerm(value = "oa:salary:export", elevation = true)   // 强制 JIT 提权
@PublicApi                                                     // 显式豁免，CI 检查认这个
```
- **切面**：`RequiresPermAspect`（Order 最高），从 `PermissionEngine` 取快照 → 位图判定 →
  失败抛 `ForbiddenException`（统一 `Result` 结构，沿用 his 的 `ResultCode` 风格）。
- **兜底过滤器**：`UrlPermissionFilter` 用 `permission(resource, method)` 表做 Ant 匹配，
  防"新加接口忘了加注解"。表由启动时扫描 + 管理端维护。
- **CI 强制**（关键工程实践）：`ControllerPermissionCoverageTest` 反射扫描所有
  `@RestController` 的 public handler，缺 `@RequiresPerm` 且无 `@PublicApi` → **构建失败**。

### 5.2 数据权限（Data）—— 行级 + 列级

**行级** —— `@DataScope` + SQL 改写：
```java
@DataScope(alias = "t", orgColumn = "org_id", pathColumn = "org_path",
           userColumn = "creator_id", module = "attendance")
List<AttendanceVO> page(AttendanceQuery q);
```
翻译规则（ADR-0007）：

| scope_type | 生成的 SQL 片段 |
|---|---|
| `ALL` | （不加条件） |
| `ORG_AND_SUB` | `t.org_path LIKE '/1/23/%'`（多范围用 OR 拼，> 8 个前缀降级 `t.org_id = ANY(?::bigint[])`） |
| `ORG` | `t.org_id = ?` |
| `SELF` | `t.creator_id = ?` |
| `CUSTOM` | 同 `ORG_AND_SUB`，前缀来自 `scope_org_ids` 展开 |

**硬要求**：所有业务单据表**必须冗余 `org_id` + `org_path`**（写入时从快照取），
`org_path` 上建 `text_pattern_ops` 索引。缺一不可。

**实现选型**：MyBatis-Plus `DataPermissionInterceptor`（若用 JPA 则 Hibernate `@Filter` + Specification）。
→ **本项目选 MyBatis-Plus**：SQL 改写场景 MyBatis 拦截器远比 JPA 直观可控；
his-platform 用 JPA，这里做**不同的技术选择并写清理由**（数据权限是 OA 的第一性问题）。

**列级（字段脱敏）**：
```java
public class EmployeeVO {
    @Sensitive(type = MOBILE,  perm = "oa:field:mobile")   private String mobile;
    @Sensitive(type = ID_CARD, perm = "oa:field:idcard")   private String idCard;
    @Sensitive(type = AMOUNT,  perm = "oa:field:salary")   private BigDecimal salary;
}
```
Jackson `BeanSerializerModifier` 在序列化时查当前快照是否含该 perm，无则脱敏（`138****1234`）。
**脱敏在序列化层做，不在业务层**——否则每个 VO 组装点都要写一遍，必漏。

### 5.3 页面权限（Menu / Button）—— 仅体验

```
GET /api/v1/me/permissions
→ { version, permCodes: ["oa:leave:apply", ...], menus: [{code,name,icon,route,children}] }
```
- 前端静态路由表 + `meta.perm` 过滤（ADR-0009）。
- 按钮：`<Can code="oa:leave:approve">` / `usePerm(code)`。
- **秒级生效**：权限变更 → `oa-notify-service` 经 WebSocket 推 `perm.changed{version}` →
  前端比对本地 version → 重拉 `/me/permissions` → zustand 更新 → **UI 不刷新即变**。

---

## 6. 业务模块全清单（18 模块，ADR-0004 一次规划到位）

| # | 域 | 模块 | 核心实体 / 要点 |
|---|---|---|---|
| 1 | 基础 | **组织人事** | `org_unit / org_closure / employee / position / employee_org_assignment / reporting_line`；组织树拖拽调整、批量导入、离职交接 |
| 2 | 基础 | **权限中心** | `permission / role / role_inherit / role_permission / grant_record / delegation`；★ 权限调试器（"为什么这个人能看到这个"） |
| 3 | 基础 | **通讯录** | 组织树懒加载 + 员工检索；★ 前端 IndexedDB 增量同步（`/directory/delta?since=`） |
| 4 | 基础 | **个人中心** | 我的信息 / 我的授权 / 我的代理 / 安全设置（改密、MFA） |
| 5 | 协同 | **工作台** | ★ `todo_item` CQRS 宽表；待办 / 我发起 / 抄送我 / 已办；数据卡片 |
| 6 | 协同 | **公告通知** | `announcement / announcement_target`；★ 已读回执用 **RoaringBitmap** 而非万行明细 |
| 7 | 协同 | **日程会议** | `calendar_event / event_attendee / meeting_room / room_booking`；★ PG `EXCLUDE USING gist` 防重叠 |
| 8 | 协同 | **站内信推送** | `oa-notify-service` 独立部署；WebSocket + 邮件 + 短信 + 企微/飞书 |
| 9 | 流程 | **表单引擎** | `form_template(JSON Schema, 版本化) / form_field`；拖拽设计器 + 渲染器；字段级权限 |
| 10 | 流程 | **审批中心** | `approval_instance / approval_node_log / approval_cc`；编排走 workflow-platform |
| 11 | 流程 | **业务单据** | 请假/加班/出差/调休/报销/借款/用印/合同/采购/入职/离职/调岗，共 12 类 |
| 12 | 考勤 | **排班打卡** | `shift / schedule / punch_record(月分区) / attendance_daily`；★ 打卡削峰 |
| 13 | 考勤 | **假期额度** | `leave_type / leave_balance / balance_txn`；★ 乐观锁 + `request_id` 幂等 |
| 14 | 文档 | **公文流转** | `official_doc / doc_flow_log`；收文/发文/用印/归档；★ 文号号段生成 |
| 15 | 文档 | **知识库网盘** | `kb_folder / kb_doc / kb_share`；★ 可选挂 auth-platform SpiceDB（`knowledge.zed` 已建模） |
| 16 | 行政 | **资产与行政** | `asset / asset_txn / supply / vehicle_booking / visitor` |
| 17 | 报表 | **管理驾驶舱** | 考勤统计 / 审批时效 / 编制人效 / 费用分析；只读数据源 + 预计算 |
| 18 | 系统 | **审计与集成** | `audit_log(月分区) / dict / sys_config`；企微钉钉飞书同步适配层、邮件、SSO |

---

## 7. 服务端架构

### 7.1 模块划分（模块化单体 + 3 独立服务，ADR-0002）

```
oa-platform/
├── oa-dependencies/         BOM，统一依赖版本
├── oa-common/               Result / 异常 / 工具 / TenantContext / 审计基础设施 / 号段生成器
├── oa-protocol/             对外 DTO + 事件契约（与 3 个独立服务、前端共享语义）+ golden 测试
├── oa-security/             JWT 解析 / UserContext / @RequiresPerm / @DataScope / @Sensitive / @PublicApi
├── oa-org/                  组织域 + OrgTreeCache + OrgQueryApi
├── oa-iam/                  权限域 + PermissionEngine + 三级缓存 + 失效广播 + 权限调试器后端
├── oa-flow/                 表单引擎 + 单据 + workflow-platform SDK 适配 + todo_item 读模型
├── oa-attendance/           排班 / 打卡 / 额度 / 考勤计算
├── oa-doc/                  公文 + 知识库（知识库可选挂 SpiceDB）
├── oa-admin-biz/            行政域：资产 / 用品 / 车辆 / 访客 / 会议室
├── oa-report/               报表（只读数据源）
├── oa-app/                  ★ 启动模块：装配全部 + Web 层 + OpenAPI
├── oa-notify-service/       ★ 独立部署 :8401，WebSocket 长连 + 多渠道推送
├── oa-file-service/         ★ 独立部署 :8402，上传/下载/预览 + MinIO
└── oa-job-service/          ★ 独立部署 :8403，跑批 + 分片调度 + 授权到期回收
```

**模块纪律（ArchUnit 强制，违反即构建失败）**：
```java
noClasses().that().resideInAPackage("..oa.attendance..")
    .should().dependOnClassesThat().resideInAPackage("..oa.org.infrastructure..");
// 跨模块只允许依赖 ..oa.<mod>.api.. （接口 + DTO）与领域事件
```

### 7.2 端口 / 中间件分配（避让现有项目）

| 组件 | 端口 | 备注 |
|---|---|---|
| `oa-app` | **8400** | 主应用 |
| `oa-notify-service` | **8401** | 含 WebSocket `/ws` |
| `oa-file-service` | **8402** | |
| `oa-job-service` | **8403** | |
| `oa-console`（PC） | **5473** dev / **8404** prod | |
| `oa-mobile`（H5） | **5474** dev / **8405** prod | |
| PostgreSQL | **35432** | 独立实例（auth 用 15432，workflow 用 25432） |
| Redis | **36379** | 缓存 + Pub/Sub + 分布式锁 |
| Kafka | **39092** | ⚠️ 9092 属 langchain4j、29092 属 workflow，**勿动** |
| MinIO | **39000 / 39001** | |
| Elasticsearch | **39200** | 可选（Phase 7），不做则降级 PG 全文索引 |
| 复用：Casdoor | 8000 | 已在跑 |
| 复用：workflow server | 8300 | 已在跑 |
| 复用：auth server | 8200 | 知识库子域用 |

**compose 纪律**（auth / risk / workflow 都踩过 docker-proxy 残留占端口）：
`name: oa-platform` + 端口全部变量化 + 起前 `docker compose -p oa-platform down --remove-orphans`。

### 7.3 与 workflow-platform 的集成契约（ADR-0010）

**发起流程（同事务 outbox → Kafka，不用同步 HTTP）**：
```
OA 单据提交 (一个事务)
  ├─ INSERT approval_instance
  ├─ INSERT form_instance
  └─ INSERT oa_outbox (topic=workflow.command.start, payload=StartProcessCommandV1)
                ↓ OutboxPublisher 轮询
        Kafka :39092 → workflow-platform 起流程
                ↓ 生命周期事件回流
        Kafka → OA TodoProjector → UPSERT todo_item（CQRS 读模型）
```

`StartProcessCommandV1` 的 `variables` 由 OA 算好塞入：
```json
{
  "applicant": "<casdoor sub>",
  "applicantOrgId": 456, "applicantOrgPath": "/1/23/456/",
  "directManager": "<sub>",
  "approverChain": ["<sub>", "<sub>"],        // 逐级上报链，OA 用 reportingLine 算
  "deptLeader": "<sub>", "hrbp": "<sub>",
  "amount": 4800, "leaveDays": 3,
  "formTemplateId": 12, "formVersion": 3
}
```

**办理动作**走 SDK `WorkflowClient`（已有接口，:8300 REST）：
`findTasks / completeReview / claimTask / reassignTask`。
> ⚠️ 现有 `WorkflowClient.completeReview` 的语义是 his 审方专用（`CompleteReviewRequest`）。
> OA 通用审批需要中台补一个 **`completeTask(tenant, taskId, outcome, variables, comment)`** 通用端点。
> **这是本计划对 workflow-platform 的唯一改造请求**，列为 Phase 4 的前置依赖，改动只增不改（新增端点 + SDK 方法）。

**新增 BPMN**（放 workflow-platform `core/src/main/resources/bpmn/`）：
`oa-generic-approval-v1`（★ 通用 N 级会签/或签，由流程变量驱动，覆盖 12 类单据中的 10 类）、
`oa-elevation-v1`（JIT 提权）、`oa-onboarding-v1`（入职多方并行）。
**每个 BPMN 必须带 BPMNDI**（否则 workflow-console 的 bpmn-js 渲染不出——该项目已踩过这个 Blocker）。

⚠️ **构建坑（必看）**：workflow-platform 制品装在 **system maven 本地仓库 `/Users/liruijun/personal/repository`**，
不在 `~/.m2`。OA 必须用 system mvn（`/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin`），
否则解析不到 `com.lrj.workflow` 制品。

### 7.4 与 auth-platform 的集成契约

- Casdoor 新建应用 `oa-platform`：授权码 + PKCE + refresh_token，
  redirect `http://localhost:5473/callback` 与 `http://localhost:5474/callback`。
- ⚠️ 已知坑（来自 auth-platform 实战）：
  - `sub` 是 **UUID**，OA 的 `employee.user_id` 存这个（**不要重蹈 his 用 Long userId 当 subject 的错配**）；
  - `name` claim 才是用户名；`groups` claim 需在 Casdoor 显式配置才进 JWT；
  - password grant 默认关闭，`app-built-in` 需 `update-application` 加 `grantTypes=["password"]` 才能 curl 取 token。
- **组织权威在 OA**，不同步给 Casdoor（Casdoor 只管账号）。可选反向：把部门推成 Casdoor group 供其它系统消费。
- 知识库子域挂 SpiceDB：`oa.authz.spicedb.enabled` 默认 `false`（Noop 实现），
  开启后经 auth-platform SDK **HTTP → :8200**（**不引 authzed gRPC**，避免依赖冲突，该项目已验证此路可行）。
- ⚠️ SpiceDB `:8543` 写 schema 是**全量替换语义**，新增 `oa.zed` 必须与
  `knowledge / his / recsys / risk` **合并成一份**再写入，否则会删掉其它定义。

---

## 8. 前端架构

### 8.1 Monorepo 结构（pnpm workspace）

```
oa-platform/frontend/
├── package.json          workspaces: packages/*, apps/*
├── packages/
│   ├── shared/           @oa/shared：API client(axios+401单飞续期) / 生成的 TS 类型 / 权限 hook / 校验
│   └── form-engine/      @oa/form-engine：JSON Schema 渲染器（PC 与 H5 共用的最重资产）
└── apps/
    ├── console/          PC 端 :5473 → React18 + Vite5 + TS + antd5 + ProComponents
    └── mobile/           H5   :5474 → React18 + Vite5 + TS + antd-mobile5（见 ADR-0003 开放项）
```

### 8.2 PC 端 `oa-console`

**技术栈**（沿用 auth-console / workflow-console，可直接克隆脚手架）：
React 18 · Vite 5 · TypeScript 5 · antd 5 · `@ant-design/pro-components`（ProTable/ProForm）·
`@tanstack/react-query` v5 · zustand · `oidc-client-ts` + `react-oidc-context` · react-router v6 ·
`@tanstack/react-virtual`（虚拟滚动）· dnd-kit（表单设计器）· bpmn-js `NavigatedViewer`（流程轨迹只读，懒加载）

**关键设计**：

1. **权限渲染体系**
   - `AuthProvider` → 登录后并发拉 `/me/profile` + `/me/permissions`
   - zustand `permStore = { permCodes: Set, menus, moduleScope, version }`
   - `usePerm(code)` / `<Can code>` / `<PermRoute code>` / `withPerm()`
   - **权限 code 常量由后端 `permission` 表生成 TS 文件**（`pnpm gen:perm`），杜绝手写字符串打错
   - WebSocket 收 `perm.changed` → 重拉 → **不刷新页面即生效**（G3 的前端一半）

2. **万人通讯录**（真难点）
   - 组织树：懒加载子节点；搜索直达时后端返回 `path`，前端自动展开到位
   - 员工列表：`@tanstack/react-virtual` 虚拟滚动 + 后端分页
   - ★ **IndexedDB 增量同步**：本地存 `{employees, version}`，启动 `GET /directory/delta?since=version`
     只拉变更 → 首屏从本地出（**秒开**），后台补齐。这是万人级通讯录的标准解法。

3. **表单引擎**（OA 前端最重投入，`@oa/form-engine`）
   - 设计器：dnd-kit 拖拽 → 输出 JSON Schema（字段 / 校验 / 联动 / 字段级权限 / 布局 / 公式）
   - 渲染器：Schema → antd 表单；支持只读态（审批查看）、字段级权限（薪资字段仅 HR 可见）
   - **版本化**：单据引用 `formTemplateId + formVersion`，模板改版不影响历史单据

4. **性能预算**
   - 路由级 lazy + 模块级 chunk（org / iam / attendance / flow / doc 各成包）
   - ProComponents 体积大 → 仅在需要的页面引入，不进主包
   - **首屏 JS < 300 KB gzip**；大表格走 ProTable + 后端分页

5. **工程化**：pnpm · ESLint + Prettier · **Vitest + RTL** 单测 · **Playwright** E2E 冒烟 · MSW mock

⚠️ **已知前端坑**（auth-console / workflow-console 实战）：
tsconfig 别用 `composite + noEmit`（TS6310）；`vite.config` 用 `loadEnv(mode,'.','')` 避 process 类型问题；
`build = tsc && vite build`；克隆 console 时**必须清掉 `authz-admin` / `authz-viewer` 等专有词**，否则人人 403。

### 8.3 移动端 `oa-mobile`（独立 H5）

**范围**：打卡（定位/WiFi）· 审批（待办/我发起/详情/同意驳回/加签）· 通讯录 · 公告 · 日程 · 我的
**非目标**：组织管理、权限配置、表单设计器、报表（这些留 PC）

**技术点**：
- 复用 `@oa/shared` 与 `@oa/form-engine`（审批详情直接复用渲染器的只读态）
- **PWA + Service Worker**：弱网/地下车库打卡先入本地队列，恢复后补传（幂等键保证不重复）
- **定位打卡**：前端只传坐标，**围栏判定在后端**（防篡改）；WiFi/蓝牙打卡预留接口
- 企微 / 钉钉 / 飞书容器内**免登**：JS-SDK 取 code → 后端换 Casdoor token
- **首屏 JS < 150 KB gzip** + 骨架屏

---

## 9. 数据库与容量设计

### 9.1 组织与 Schema

PostgreSQL 16 单实例 :35432，Flyway 迁移。按模块分 schema（`oa_org` / `oa_iam` / `oa_flow` / `oa_att` /
`oa_doc` / `oa_admin` / `oa_sys`）但**同库**——模块化单体下跨模块事务可用；
纪律上**禁止跨 schema join**（ArchUnit 管不到 SQL，靠 code review + 集成测试断言），为将来拆库留缝。

### 9.2 容量估算（万人级，设计必须建立在数字上）

| 指标 | 估算 |
|---|---|
| 员工 / 组织单元 / 岗位 / 角色 / 权限点 | 10,000 / ~3,000 / ~500 / ~200 / ~800 |
| DAU / 并发会话 | ~7,000 / ~9,000 |
| `punch_record` | 10,000 × 2 次 × 250 天 = **500 万 / 年** |
| `attendance_daily` | 250 万 / 年 |
| `notification` | 10,000 × 20 × 250 = **5,000 万 / 年（最大表）** |
| `approval_instance` | 10,000 × 20 = 20 万 / 年 |
| `audit_log` | ~1 亿 / 年（含读审计则更多，故只审计写 + 敏感读） |

**分区**（声明式，按月）：`punch_record` · `notification` · `audit_log` · `attendance_daily`。
3 个月前的分区 detach 归档到冷表 / 对象存储。

### 9.3 三个峰值场景与对策

**① 早高峰打卡** —— 10,000 人 × 1.5 次集中在 9:00±15min，若 60% 落在 5 分钟内 → **瞬时 300–500 QPS**
```
移动端 → oa-app /attendance/punch
  ① Redis SETNX  punch:{userId}:{date}:{type}   ← 幂等，重复打卡直接返回成功
  ② 写入本地阻塞队列（有界，满则降级直写 DB）
  ③ 立即返回 200（用户感知 < 50ms）
  ④ 后台批量线程 500 条 / 200ms 批量 INSERT
  ⑤ 失败进 oa_outbox 重试，永不丢
```

**② 全员公告广播** —— 一次 10,000 推送 + 已读回执
- 推送：`oa-notify-service` 按在线连接分批（1,000/批）异步推，离线用户下次登录拉
- **已读回执用 RoaringBitmap**：`announcement_read_bits(announcement_id, bitmap bytea)`，
  员工映射为稠密序号。1 万人的位图压缩后 **几 KB**，替代 1 万行明细表。
  需要明细时（谁没读）由位图反查 employee 序号表。

**③ 月初考勤跑批** —— 10,000 人 × 30 天 = 30 万条聚合
- `oa-job-service` 分片调度：按 `employee_id % 16` 分 16 片并行，夜间跑
- 白天走增量（打卡事件驱动当日汇总），月结只做校验与补算

### 9.4 并发写点与方案（逐个点名，不含糊）

| 场景 | 方案 | 理由 |
|---|---|---|
| 假期额度扣减 | `leave_balance.version` 乐观锁 + `balance_txn(request_id)` 唯一约束幂等 | 同一人额度天然低并发，分布式锁是杀鸡用牛刀 |
| **会议室预定** | ★ PG `EXCLUDE USING gist (room_id WITH =, during WITH &&)` | **数据库层杜绝时间重叠**，比应用层加锁可靠得多 |
| 单据编号 / 公文文号 | **号段模式**（一次取 1,000 号缓存本地） | 避免 `SELECT ... FOR UPDATE` 成为串行瓶颈 |
| 组织树移动 | 单事务 + `org_unit.version` 乐观锁 + 全局重建事件 | 低频操作，正确性优先 |
| 审批并发操作同一单 | `approval_instance.version` 乐观锁 + 幂等 `actionId` | 双人同时点"同意"必须只生效一次 |
| 资产领用 | 行锁 `SELECT ... FOR UPDATE` + 状态机校验 | 库存类，量小，简单可靠 |

### 9.5 JVM / 连接池

- JDK 21 **虚拟线程**：`spring.threads.virtual.enabled=true`（沿用 his 惯例）
- ⚠️ **风险**：虚拟线程遇 `synchronized` 会 **pin 载体线程**。HikariCP / 部分 MyBatis 版本存在 synchronized 块，
  高并发下可能退化。→ **Phase 0 硬门禁：必须实测**（`-Djdk.tracePinnedThreads=full` 压测观察）。
  不过则回退平台线程池 + 明确记录。
- HikariCP `maximumPoolSize` 按 `CPU核数 × 2 + 有效磁盘数` 设定，**不盲目调大**
  （虚拟线程下调大反而更容易打满 DB）。

---

## 10. 安全与合规

- **认证**：Casdoor OIDC 授权码 + PKCE；access_token 15min + refresh_token；网关本地 JWK 校验（JWKS 缓存 1h）
- **主体标识**：全系统统一用 Casdoor `sub`（UUID）。**禁止**用自增 Long 当 subject
- **越权（IDOR）防护**：所有 by-id 查询必须经数据权限；Phase 8 有专门的 IDOR 渗透用例
- **敏感字段**：身份证 / 手机 / 银行卡 **AES-GCM 应用层加密**（密钥走环境变量，不入库不入 git），
  同时存**确定性 HMAC 哈希列**供精确查询；序列化层 `@Sensitive` 脱敏
- **审计**：`audit_log` 记录 who / when / what / where / before / after / elevated；
  **权限变更、JIT 提权、数据导出强制审计**；只增不改，月分区
- **导出管控**：单次条数上限 + 频率限制 + 导出内容水印 + 全量审计
- **接口防刷**：登录 / 打卡 / 导出走 Redis 令牌桶限流
- **前端不是安全边界**：Phase 8 必须有用例证明"前端藏了按钮，后端依然 403"

---

## 11. 分期实施计划（Phase 0–8）

> 每个 Phase 结束都要有：可运行的冒烟脚本 `deploy/scripts/*-smoke.sh`（本机 **Testcontainers 跑不起来**，
> 见 [[local-dev-env]]）、更新 `IMPLEMENTATION_PROGRESS.md`、以及明确的回滚路径。

### Phase 0 —— 基建与硬门禁（依赖：无）
1. 项目骨架：父 pom + `oa-dependencies` BOM + 15 个模块目录，全部能 `mvn -q compile`
2. `deploy/docker-compose.yml`：PG 35432 / Redis 36379 / Kafka 39092 / MinIO 39000，
   `name: oa-platform` + 端口变量化
3. Casdoor 建 `oa-platform` 应用（PKCE + refresh_token + 双 redirect）
4. ★ **硬门禁 A**：JDK21 虚拟线程 + HikariCP + MyBatis-Plus **pinning 实测**（`-Djdk.tracePinnedThreads=full`）
5. ★ **硬门禁 B**：用 system mvn 验证能解析 `com.lrj.workflow:workflow-platform-sdk`（`/personal/repository`）
6. ArchUnit 骨架 + `ControllerPermissionCoverageTest` 骨架（先空跑，Phase 2 生效）
7. **验收**：`deploy/scripts/phase0-smoke.sh` —— 容器全起 + 空应用 8400 健康 + 两个门禁结论落文档

### Phase 1 —— 组织域（依赖 Phase 0）
`org_unit / org_closure / employee / position / employee_org_assignment / reporting_line` 建表 +
闭包维护（增/移/停）+ `OrgTreeCache`（COW）+ `OrgQueryApi` + 组织/员工 CRUD API + **10,000 员工批量导入**（COPY）
**验收**：造 3,000 组织 + 10,000 员工；移动一棵子树后 `descendantIds` / `pathOf` 立即正确；
`asOf` 能还原 3 个月前的任职；导入 10,000 员工 < 30s

### Phase 2 —— 权限域 ★ 最关键（依赖 Phase 1）
`permission / role / role_inherit / role_permission / grant_record / delegation` +
`PermissionEngine` 三级缓存 + 失效广播 + `@RequiresPerm` / `@DataScope` / `@Sensitive` / `@PublicApi` +
CI 覆盖检查生效 + 影子校验 + JIT 提权与委托代理的服务端逻辑 + 权限调试器后端 API
**验收**：`phase2-perm-smoke.sh` —— 部门授权→子部门员工立即有权 / 撤权立即失效 /
临时授权到点自动失效 / 委托期间代理人可办且留 `on_behalf_of` / 数据权限 SQL 确实带上 `org_path LIKE` /
判权 P99 < 1ms（JMH 或 k6）

### Phase 3 —— PC 前端底座（依赖 Phase 2）
> ⚠️ 按全局规范，PC 前端**动手前先走 `/frontend-plan`**（auth-console / workflow-console 都是这么做的）

monorepo + `@oa/shared` + 脚手架（克隆 auth-console）+ Casdoor SSO + 权限渲染体系 +
组织管理页（树拖拽）+ 员工管理页 + 角色/授权管理页 + ★ **权限调试器页**（"为什么这个人能看到这个"）
**验收**：改角色后前端不刷新即生效；权限调试器能解释一条判定的完整来源链

### Phase 4 —— 工作台 + 审批底座（依赖 Phase 2、Phase 3）
前置：**给 workflow-platform 补通用 `completeTask` 端点 + SDK 方法**（只增不改）
`@oa/form-engine`（设计器 + 渲染器）+ `form_template` 版本化 + `oa_outbox` → Kafka 起流程 +
`todo_item` CQRS 投影 + 通用审批 BPMN `oa-generic-approval-v1`（含 BPMNDI）+ **请假单端到端闭环**
**验收**：请假单 → 审批人由汇报线动态算出 → 待办出现在工作台 → 审批通过 → 额度扣减 → 通知送达

### Phase 5 —— 考勤域（依赖 Phase 4）
排班 / 打卡（削峰链路）/ 假期额度 / 加班调休 / 考勤日结 / `oa-job-service` 分片跑批 / 考勤报表
**验收**：k6 打卡 **500 QPS × 5min，错误率 0，无重复打卡**；月结 10,000 人 < 10min

### Phase 6 —— 通知与移动端（依赖 Phase 4）
`oa-notify-service`（WebSocket + 多渠道 + 公告位图回执）+ `oa-mobile` H5（打卡 + 审批 + 通讯录 + 公告）
> ⚠️ 移动端同样先走 `/frontend-plan`；ADR-0003 的框架开放项需在此前定
**验收**：全员公告 10,000 推送完成 < 30s；离线打卡补传不重复；H5 首屏 < 150KB gzip

### Phase 7 —— 文档与行政（依赖 Phase 4）
公文流转（含号段文号）+ 知识库/网盘（**可选挂 SpiceDB**，`oa.authz.spicedb.enabled` 默认 false）+
资产 / 用品 / 车辆 / 访客 / **会议室（exclusion constraint）** + `oa-file-service`
**验收**：会议室并发预定 100 请求只成功 1 条；知识库开关打开后跨部门共享判权正确、关闭后回退无影响

### Phase 8 —— 报表 / 审计 / 压测 / 交付（依赖全部）
管理驾驶舱 + 审计报表 + **渗透用例**（越权 / IDOR / 前端绕过）+ k6 全链路压测 +
nginx 交付（8404 / 8405）+ 文档（README / ADR / 运维手册）+ `/doc-sync`
**验收**：见第 13 节

---

## 12. 测试方案

| 层 | 内容 | 工具 |
|---|---|---|
| 单元 | 闭包维护、快照合并、SQL 改写、脱敏、位图 | JUnit5 + AssertJ |
| 架构 | 跨模块依赖纪律、Controller 权限覆盖 | ArchUnit + 自研反射扫描 |
| 契约 | `oa-protocol` DTO golden 测试（防误改破坏前端/独立服务） | golden JSON 对比（沿用 workflow-platform 做法） |
| 集成 | 打运行中的 compose 容器；**Docker 不可用则 `assumeTrue` 跳过**（本机 Testcontainers 不可用） | Spring Boot Test |
| 冒烟 | 每 Phase 一个 `deploy/scripts/phaseN-smoke.sh`，断言式输出 | bash + curl + jq |
| 压测 | 打卡 500 QPS、判权 P99、工作台首屏、全员广播 | k6 |
| 安全 | 越权矩阵、IDOR、前端绕过、导出限流 | 自研用例 + 手工 |
| 前端 | 组件单测 + E2E 冒烟（登录→权限渲染→提单→审批） | Vitest + RTL + Playwright |

---

## 13. 验收标准（G1–G7 的可测量形式）

- ✅ **权限秒级生效**：`phase2-perm-smoke.sh` 全绿——改组织 / 改角色 / 临时授权 / 委托 / 撤权，
  **菜单、按钮、接口、数据范围**四处立即随之变化
- ✅ **判权 P99 < 1ms**（本地，不含网络）；**工作台首屏 API P99 < 200ms**
- ✅ **打卡 500 QPS 持续 5min**：错误率 0、无重复打卡记录
- ✅ **全员公告**：10,000 人推送完成 < 30s，已读回执存储 < 100KB
- ✅ **越权测试全过**：低权限用户直调高权限接口 403；改 URL id 越权取数返回 403/空；
  前端隐藏的按钮对应接口后端依然拦截
- ✅ **组织能力**：一人多岗、虚线汇报、任意层级、历史 `asOf` 还原全部可演示
- ✅ **前端预算**：PC 首屏 JS < 300KB gzip；H5 < 150KB gzip
- ✅ **工程纪律**：ArchUnit 通过、Controller 权限覆盖 100%、契约 golden 测试通过

---

## 14. 风险清单

| # | 风险 | 影响 | 对策 |
|---|---|---|---|
| R1 | **JDK21 虚拟线程 pinning**（HikariCP/MyBatis synchronized） | 高并发退化 | **Phase 0 硬门禁**实测；不过则回退平台线程池 |
| R2 | **权限缓存失效不正确** | 改权不生效 = **安全事故**；全量刷 = 性能事故 | 混合失效策略 + **影子校验** + `oa_perm_mismatch_total` 指标告警 |
| R3 | **PG 前缀 LIKE 不走索引** | 数据权限查询全表扫 | 强制 `text_pattern_ops` 索引，写进 Flyway；Phase 2 用 `EXPLAIN` 断言 |
| R4 | **org_path 长度溢出** | 深层组织截断 | `varchar(512)`，9 层 × 8 字符仍有余量；建表加 check |
| R5 | **组织移动的一致性** | closure 与 path 不一致 | 单事务 + 乐观锁 + 事后校验任务（`org_consistency_check`） |
| R6 | **workflow 制品仓库错配**（system mvn vs mvnw） | 构建失败 | Phase 0 硬门禁 B；README 显式写死用 system mvn |
| R7 | **SpiceDB schema 全量替换** | 删掉 knowledge/his/recsys/risk 定义 | 加 `oa.zed` 必须合并写入；Phase 7 前先补脚本 |
| R8 | **端口冲突 / docker-proxy 残留** | 起不来（该团队踩过 3 次） | compose `name:` + 变量化 + 起前 `down --remove-orphans` |
| R9 | **万人初始化导入慢** | Phase 1 卡住 | `COPY` 批量导入，非逐条 insert |
| R10 | **通知表膨胀 5,000 万/年** | 查询变慢、磁盘爆 | 月分区 + 3 个月归档 + 已读位图 |
| R11 | **前端权限被当成安全边界** | 越权漏洞 | ADR-0008 写死 + CI 覆盖检查 + Phase 8 渗透用例 |
| R12 | **表单引擎范围失控** | Phase 4 无限延期 | 首版**只做 15 种字段类型 + 简单联动**，公式/子表单押后 |
| R13 | 移动端框架未定（Vant vs antd-mobile） | Phase 6 返工 | ADR-0003 列为开放项，Phase 6 前必须拍板 |
| R14 | workflow-platform 通用 `completeTask` 端点未就绪 | Phase 4 阻塞 | 列为 Phase 4 前置，只增不改，风险低 |

---

## 15. 回滚与灰度

- 每个可能影响既有系统的接入点都有**开关且默认关**：
  `oa.authz.spicedb.enabled=false` · `oa.workflow.enabled=false` · `oa.iam.abac.enabled=false` ·
  `oa.iam.shadow-verify.enabled=true`（观测用，可关）
- **对 workflow-platform 与 auth-platform 的改动一律"只增不改"**（新增端点 / 新增 zed definition），
  不改既有行为，因此这两个系统的现有消费方（his）零影响
- 数据库迁移全部 Flyway，向前兼容；破坏性变更拆成"加列 → 双写 → 切读 → 删列"四步
- 每 Phase 的冒烟脚本即回归基线，回滚以"关开关 + 回退 jar"为主

---

## 16. 首个可演示里程碑（建议对外展示的那一刀）

**"权限沙盘"**：一个页面演示完整能力——
左侧组织树（可拖拽调整），中间某员工的**权限来源解释**（来自哪个部门继承 / 哪个角色 / 哪条临时授权），
右侧实时预览"该员工看到的菜单 + 能调的接口 + 能查到的数据行数"。
拖动组织 / 加一条临时授权，右侧**立即变化**。

这一个页面同时证明了 G1（组织模型）、G2（权限体系）、G3（秒级生效），
是整个项目最有说服力的展示面，对应 Phase 3 的权限调试器页。
