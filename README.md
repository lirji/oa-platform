# oa-platform

企业级 OA 协同办公平台，目标规模 **10,000 员工**。**模块化单体 + 3 个独立服务**。

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase 0 | 项目骨架 + 基建 + 两道硬门禁 | ✅ 冒烟 14/14 |
| Phase 1 | 组织域（任意层级 / 一人多岗 / 汇报线 / 时间维度） | ✅ 冒烟 28/28 |
| Phase 2 | 权限域（三级缓存判权 / 三类权限 / 临时授权三形态） | ✅ 冒烟 32/32 |
| Phase 4 | 审批底座（事务发件箱 / 待办读模型 / 请假闭环） | ✅ 冒烟 25/25 |
| Phase 4b | ★ 接真 workflow-platform（通用审批端到端） | ✅ 冒烟 28/28 |
| Phase 5 | 考勤（早高峰削峰 / 分区 / 日结） | ✅ 冒烟 19/19 |
| Phase 6 | 通知（长连 / 万人公告 / 位图回执）— 后端 | ✅ 冒烟 28/28 |
| Phase 7 | 文档与行政（公文号段 / 知识库 / 会议室排他约束） | ✅ 冒烟 45/45 |
| Phase 8 | 报表 / 审计 / 跑批 / 文件 / ★渗透用例 | ✅ 冒烟 40/40 |
| Phase 3 · 6 | PC 前端 `oa-console` · 移动端 `oa-mobile` | ✅ 发布关卡 14/14 · 13/13 |
| 交付收口 | OpenAPI 契约 / CI / JWT+WS / 全链路压测 | ✅ 全部通过 |
| 权限增强 | USER_GROUP 显式成员组 / 受限 ABAC 条件引擎 | ✅ Phase 9 冒烟通过 |
| 角色治理 | 自定义角色 CRUD / 权限矩阵 / 角色继承 / 启停与审计 | ✅ 全链路验收通过 |

**后端 231 条阶段冒烟断言全绿，PC、移动端、JWT/WebSocket 和发布门禁均已完成。**
最终验收证据见 [`docs/delivery/oa-platform-completion/`](docs/delivery/oa-platform-completion/)。

### 几个拿得出手的数字

| 能力 | 实测 | 验收线 |
|---|---|---|
| 判权 P99 | **1.25 μs** | 1 ms |
| 打卡吞吐（万人早高峰） | **512 QPS**，0 失败 0 重复 | 500 QPS |
| 全员公告推送（10,000 人） | **502 ms** | 30 s |
| 已读回执存储（8,000 人已读） | **15 字节**（RoaringBitmap run 编码） | 100 KB |
| 会议室并发预定（100 请求抢同一时段） | **恰好 1 成功**（PG 排他约束） | 只成功 1 条 |
| 万人组织装载（1,000 组织 + 10,000 员工） | **~0.7 s** | 30 s |
| 考勤日结（10,000 人） | **165 ms** | 10 min |
| 工作台首屏 API P99（最终 1000 请求 / 并发 20） | **63.3 ms** | 200 ms |

---

## 它解决什么

不是"又一个 CRUD 后台"。真正吃掉工期、也真正埋事故的只有两件事：

**一、组织与权限的建模深度。** 万人企业的组织层级不固定（实测 6–9 层），还随时越级挂载；
再叠上一人多岗、独立于组织树的汇报线、部门权限继承、临时授权与委托代理，
以及页面 / 接口 / 数据三类权限要一致落地。

**二、判权在热路径上。** 判权发生在每一个请求上，组织范围计算发生在每一条 SQL 上。
任何"每次判权查一次库"或"每次判权调一次远程"的设计，在万人规模下都先垮在这里。

本平台的答案：判权彻底**本地内存化**（组织树常驻 + 权限位图快照三级缓存），
数据权限走**物化路径前缀匹配**而不是把子部门展开成五千元素的 `IN` 列表。

---

## 已验证的关键指标

| 指标 | 实测 | 验收线 |
|---|---|---|
| 判权延迟 | **P99 = 1.25 μs**（p50 = 0.25 μs） | < 1 ms |
| 撤权生效 | **1 秒内**（走全局纪元） | 立即 |
| 打卡吞吐 | **512 QPS**，零失败零重复 | 500 QPS |
| 打卡单请求延迟 | P50 = 3 ms / P99 = 53 ms（低并发实测） | — |
| 万人组织装载 | 3,000 组织 + 10,000 员工 + 19,182 闭包 ≈ **1 秒** | 30 秒 |
| 万人考勤日结 | **165 ms**（聚合全在 SQL 里） | 10 分钟 |
| 移动 341 节点子树 | closure 与 path **零不一致** | 0 |

---

## 快速开始

### ⚠️ 构建必须用 system maven

中台制品（`com.lrj.workflow` / `com.lrj.authz`）装在 **`/Users/liruijun/personal/repository`**，
不在 `~/.m2`。**本项目刻意不提供 `mvnw`** —— 用它会走 `~/.m2` 从而解析不到中台制品。

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin:$PATH"
```

### 只起基建（本地开发推荐：应用跑在 IDE 里好调试）

```bash
cp deploy/.env.example deploy/.env
docker compose -p oa-platform -f deploy/docker-compose.yml down --remove-orphans
docker compose -p oa-platform -f deploy/docker-compose.yml up -d
mvn clean install -DskipTests
OA_SECURITY_MODE=DEV OA_ORG_SEED_ENABLED=true OA_BOOTSTRAP_ADMIN=<你的 Casdoor sub> \
  java -jar oa-app/target/oa-app-0.1.0-SNAPSHOT.jar
```

### 起容器化全栈

```bash
bash deploy/scripts/provision-oa-casdoor.sh  # 幂等创建 localhost OA SPA 应用
bash deploy/build-images.sh                 # 默认构建 JWT 后端 + PC + 移动端
docker compose -p oa-platform -f deploy/docker-compose.yml --profile apps up -d
curl -s localhost:8400/api/v1/system/ping
```

发布前完整冷构建：

```bash
OA_BUILD_RUN_TESTS=true OA_DOCKER_PULL=true OA_DOCKER_NO_CACHE=true bash deploy/build-images.sh
```

入口：PC `http://localhost:8404`，移动端 `http://localhost:8405`。生产默认 `JWT`；
`DEV` 只供本地冒烟显式使用，JWT 模式会拒绝 `X-OA-User` 身份覆盖。
若 `deploy/.env` 覆盖了端口，应以 `docker compose ... ps` 显示的宿主端口为准。

只更新应用镜像时无需重启数据库等基建：重新运行 `build-images.sh` 后，对
`oa-app oa-notify oa-file oa-job oa-console oa-mobile` 执行 `up -d --force-recreate` 即可。

### 前端本地开发与契约

```bash
cd oa-console
pnpm install --frozen-lockfile
pnpm gen:api:check                 # 固定 OpenAPI 快照 → TS 类型漂移门禁
pnpm test && pnpm build && pnpm size
pnpm dev                           # :5473

cd ../oa-mobile
pnpm install --frozen-lockfile
pnpm test && pnpm build && pnpm size
pnpm dev                           # :5474
```

### 冒烟脚本（每个阶段一个，全部可复现）

```bash
bash deploy/scripts/phase0-smoke.sh              # 基建 + 两道硬门禁          14 断言
bash deploy/scripts/phase1-org-smoke.sh          # 组织域                    28 断言
bash deploy/scripts/phase2-perm-smoke.sh         # 权限域                    32 断言
bash deploy/scripts/phase4-flow-smoke.sh         # 审批底座（本地替身）       25 断言
bash deploy/scripts/phase4b-remote-smoke.sh      # 审批·接真中台（集成）      28 断言
bash deploy/scripts/phase5-attendance-smoke.sh   # 考勤 + 早高峰压测         19 断言
bash deploy/scripts/phase6-notify-smoke.sh       # 通知 + 万人公告 + 位图回执 28 断言
bash deploy/scripts/phase7-doc-admin-smoke.sh    # 公文/知识库/会议室排他约束 45 断言
bash deploy/scripts/phase8-report-audit-smoke.sh # 报表/审计/跑批/文件/渗透   40 断言
bash deploy/scripts/phase3-console-smoke.sh      # PC 契约/单测/E2E/镜像/代理 14 断言
bash deploy/scripts/phase4-jwt-ws-smoke.sh       # Casdoor/JWT/WS ticket       13 断言
bash deploy/scripts/phase5-mobile-smoke.sh       # 移动四主流程/镜像/代理       13 断言
bash deploy/scripts/phase9-iam-policy-smoke.sh   # USER_GROUP + ABAC           10 关卡

# 压测（单文件 Java 程序，无需构建、无需装 k6）
java deploy/scripts/PunchLoadTest.java      http://localhost:8400 10000 300   # 早高峰打卡
OA_NOTIFY_BASE=http://localhost:8401 OA_ADMIN=seed-user-1 \
  java deploy/scripts/FullChainLoadTest.java http://localhost:8400 1000 20     # 全链路读路径
```

`phase4` 与 `phase4b` 是**两件事**：前者用本地流程替身验 OA 自己的逻辑（不依赖外部进程），
后者接真 workflow-platform 验**集成**。两个都要绿。

### CI 纪律（构建期，`mvn test`）

| 检查 | 守什么 |
|---|---|
| `ControllerPermissionCoverageTest` / `*AuthorizationStanceTest` | 每个 handler 必须声明授权立场；**四个可部署单元都覆盖** |
| `ApiSurfaceGoldenTest` / 各服务 API Golden | 冻结 135 个端点的「方法 + 路径 → 权限点/公开立场」，偷改会让构建失败 |
| `DataAccessSurfaceGoldenTest` | 冻结已迁移数据方法的「权限点 → 目标表 → 模式」 |
| `JdbcBypassArchitectureTest` | application/web 直接 JDBC 迁移基线只能减少，禁止新增或关闭数据权限拦截器 |
| `ArchitectureRulesTest` | 跨模块只走 `..api..`；Controller 不直连 Mapper |
| `Phase0PinningProbeTest` | 虚拟线程 pinning 回归 |
| `CompleteTaskVariableGuardTest`（中台侧） | 通用办理的 variables 不能伪造办理人 |

> 本机 Testcontainers 跑不起来（见 `docs/RUNBOOK.md`），所以集成验证一律走
> "bash 冒烟脚本打运行中的 compose 容器"。

---

## 端口

| 组件 | 端口 | 组件 | 端口 |
|---|---|---|---|
| oa-app 主应用 | 8400 | PostgreSQL | 35432 |
| 通知推送（WebSocket） | 8401 | Redis | 36379 |
| 文件服务 | 8402 | Kafka | 39092 |
| 跑批服务 | 8403 | MinIO | 39000 / 39001 |
| PC 管控台 | 5473 / 8404 | 复用 Casdoor | 8000 |
| 移动端 H5 | 5474 / 8405 | 复用流程中台 | 8300 |

刻意避让：9092 属 langchain4j-platform，29092 与 25432 属 workflow-platform，15432 属 auth-platform。

---

## 模块

```
oa-common          Result/异常/租户上下文/敏感字段加解密/号段/jsonb TypeHandler/缓存失效总线
oa-protocol        对外 DTO 与事件契约
oa-security        JWT/UserContext/@RequiresPerm/@DataScope/@Sensitive/@PublicApi
                   ★ 只定义判权端口，不含实现——任何模块依赖注解都不会把权限域实现拖进来
oa-org             组织域 + OrgTreeCache（COW 内存树）+ OrgQueryApi
oa-iam             权限域 + PermissionEngine 三级缓存（PermissionChecker 的实现方）
                   + 在线角色/权限矩阵/继承治理 + USER_GROUP 时间窗成员 + 快照化受限 ABAC 条件分支
oa-flow            表单引擎 + 审批 + 事务发件箱 + 待办读模型 + 请假
oa-attendance      排班 / 打卡削峰 / 日结
oa-doc oa-admin-biz oa-report    公文/知识库、行政、报表/审计
oa-app             ★ 主应用 :8400，装配以上全部
oa-notify-service  ★ 按【连接数】扩容，故独立
oa-file-service    ★ 大流量 IO，故独立
oa-job-service     ★ 跑批与在线争 CPU，故独立
oa-console         PC 控制台 :5473 / :8404（权限驱动菜单、组织/IAM/知识库/报表）
oa-mobile          员工 H5 :5474 / :8405（待办、打卡、通讯录、公告）
```

---

## 四条不可动摇的纪律

1. **后端授权才是安全边界。** 前端的菜单/按钮裁剪只是体验。每个 handler 必须有
   `@RequiresPerm` 或 `@PublicApi(reason=…)` —— `ControllerPermissionCoverageTest` 会让构建失败，
   运行期还有 fail-closed 拦截器兜底；返回业务数据的查询还要进入 `@DataScope` 行级协议。
2. **跨模块只能走 `..api..`。** 直接注入对方 Mapper/Entity 会被 `ArchitectureRulesTest` 拦下。
3. **业务单据表必须冗余 `org_id` + `org_path`**，且 `org_path` 索引必须是 `text_pattern_ops`
   （PG 非 C locale 下普通索引不走前缀 LIKE）。
4. **主体标识统一用 Casdoor `sub`（UUID）**，不许用自增 Long。

---

## 文档

| 文档 | 内容 |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 架构全景：组织建模、判权引擎、三类权限、审批链路、削峰 |
| [docs/AUTHORIZATION.md](docs/AUTHORIZATION.md) | 权限总指南：RBAC、ABAC、接口/数据/字段权限、编辑入口与全局保障边界 |
| [docs/ABAC.md](docs/ABAC.md) | ABAC 授权维度、表达式上下文、组合规则、启停和管理端编辑指南 |
| [docs/ADR.md](docs/ADR.md) | 决策记录，含**被否掉的方案**与实现期新增的决策 |
| [docs/RUNBOOK.md](docs/RUNBOOK.md) | 运维手册：启停、排障、**踩过的坑清单** |
| [docs/API.md](docs/API.md) | 接口一览与权限点对照 |
| [docs/DATABASES_AND_COMPONENTS.md](docs/DATABASES_AND_COMPONENTS.md) | 数据库、缓存、消息队列、对象存储、外部平台与前后端组件清单 |
| [角色管理交付报告](docs/delivery/role-management/DELIVERY_REPORT.md) | 角色治理范围、验收证据、发布与回滚说明 |
| [数据权限全局保障方案](docs/plans/data-permission-global-guard/FINAL_PLAN.md) | 已落地的平台强制协议、全仓迁移阶段与验收标准 |
| `docs/plans/oa-platform-0819-1721/` | 原始规划（FINAL_PLAN 845 行）与**权威进度文件** |
| `docs/plans/oa-console-0820-0500/` | PC 控制台的决策记录与 15 步实施计划 |
