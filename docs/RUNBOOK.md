# 运维手册

---

## 启停

```bash
# 环境变量（每个新 shell 都要）
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin:$PATH"

# 基建（起之前先 down --remove-orphans，清 docker-proxy 残留）
docker compose -p oa-platform -f deploy/docker-compose.yml down --remove-orphans
docker compose -p oa-platform -f deploy/docker-compose.yml up -d

# 容器化全栈
bash deploy/scripts/provision-oa-casdoor.sh  # localhost：幂等注册 OA SPA
bash deploy/build-images.sh
docker compose -p oa-platform -f deploy/docker-compose.yml --profile apps up -d

# 已有全栈只更新应用：保留 PostgreSQL/Redis/Kafka/MinIO 与数据卷
docker compose -p oa-platform -f deploy/docker-compose.yml --profile apps up -d --force-recreate \
  oa-app oa-notify oa-file oa-job oa-console oa-mobile

# 收尾
docker compose -p oa-platform -f deploy/docker-compose.yml --profile apps down
pkill -f 'oa-app-.*\.jar'
```

默认 profile 是生产等价的 `JWT`：PC 在 `:8404`，移动端在 `:8405`。只跑传统本地冒烟时，
必须在命令上显式写 `OA_SECURITY_MODE=DEV`；不要把 DEV 写回默认配置。

`build-images.sh` 默认从当前源码构建四个后端、PC、移动端共六个镜像。只验证某一前端时可用
`OA_BUILD_CONSOLE=false` 或 `OA_BUILD_MOBILE=false` 跳过另一端，不能复用来源不明的旧后端 tag。
完整冷构建使用：

```bash
OA_BUILD_RUN_TESTS=true OA_DOCKER_PULL=true OA_DOCKER_NO_CACHE=true bash deploy/build-images.sh
```

该命令先执行全仓 `mvn clean package`，再对六个镜像执行 `docker build --pull --no-cache`。

> ⚠️ 9092 属 langchain4j-platform、29092/25432 属 workflow-platform、15432 属 auth-platform，
> **别动别人的容器**。

---

## 健康检查清单

| 检查 | 命令 | 期望 |
|---|---|---|
| 应用存活 | `curl -s :8400/api/v1/system/ping` | `code:0`，`virtualThreads:true` |
| 依赖健康 | `curl -s :8400/actuator/health` | `db` / `redis` 均 UP |
| 组织树缓存 | `GET /api/v1/org/units/cache-stats` | `snapshotVersion == dbVersion` |
| 闭包一致性 | `GET /api/v1/org/units/consistency` | `inconsistencies: 0` |
| 判权缓存 | `GET /api/v1/iam/admin/cache-stats` | **`shadowMismatches: 0`** |
| 判权延迟 | `GET /api/v1/iam/admin/bench?userId=…` | `p99Us < 1000` |
| 角色管理迁移 | 查询 `flyway_schema_history` 的 version=16 | `success=true`，`role_inherit_edge` 存在 |
| JIT 审批迁移 | 查询 `flyway_schema_history` 的 version=17 | `success=true`，`elevation_request` 与审批权限存在 |
| 角色管理契约 | `GET /v3/api-docs` 检查 `/api/v1/iam/role-admin` | 9 个管理端点存在；无 token 调用返回 401 |
| 数据权限严格执行 | `/actuator/metrics/oa_data_scope_missing_total` 与 `...alias_mismatch_total` | 不增长；任何增长立即排查 |
| 发件箱 | `GET /api/v1/flow/admin/status` | `dead: 0`，`pending` 不持续增长 |
| 打卡链路 | `GET /api/v1/attendance/admin/stats` | `deadLettered: 0`，`duplicates: 0` |
| PC / Mobile | `curl -I :8404/healthz` / `curl -I :8405/healthz` | HTTP 204 |
| JWT 隔离 | 无 token 或只带 `X-OA-User` 调受保护接口 | HTTP 401 |

---

## 排障

权限模型、编辑入口和平台覆盖边界先查
[权限体系与全局保障指南](AUTHORIZATION.md)；这里仅记录运行期诊断步骤。

### 「改了权限怎么没生效」

先看 `GET /api/v1/iam/admin/explain?userId=…&permCode=…`：

- `consistent: false` → 缓存与真值漂了，看 `cachedEpoch` vs `truthEpoch`；
  纪元没推进说明写侧漏了失效调用。
- `consistent: true` 但结果不符预期 → 问题在授权本身，
  查 `GET /api/v1/iam/grants?subjectType=USER&subjectId=…`。
- `requiresElevation: true` 且 `currentlyElevated: false` → 高危操作需要先申请 JIT 提权。

**收权最长 1 秒生效**（本地纪元缓存窗口），**给个人加权在本节点立即生效**、
跨节点靠失效总线（毫秒）或 TTL（5 分钟）兜底。

### 「角色保存冲突、停用后权限仍存在」

- HTTP 409 且提示版本冲突：另一管理员已更新该角色。重新读取详情取得新 `version`，核对差异后再提交；
  不要直接改库或重复覆盖。
- 继承保存 409：检查是否继承自己、形成环路，或引用了其他租户/已删除角色。
- 停用后新授权或 JIT 应直接拒绝；已有权限最长按纪元刷新窗口在 1 秒内退出。超过窗口时检查
  `role-status#<id>` 失效日志、`perm_epoch` 是否推进，以及 `role_inherit` 是否仍包含该停用角色。
- 角色无法删除通常表示仍有 `grant_record` 历史。该保护是审计要求；应停用角色或保留记录，不能物理删表。

### 「查出来的数据比预期多/少」

先确认调用的是已迁移的 `@DataScope` 方法。注解中的 `permission` 必须是入口 RBAC/ABAC 实际通过的
权限点，`table` 和 `alias` 必须与 SQL 一致；数据范围按该权限点单独计算，不再读取模块最宽范围。
用 `GET /api/v1/me/permissions` 核对权限和范围，并查看上述 `oa_data_scope_*` 指标。

`OA_DATA_SCOPE_STRICT=true` 时，目标 SQL 没有被 MyBatis 消费、表名/alias 错配、条件解析失败都会直接
失败，不会静默返回全量。`GovernedTableRegistry` 中的已迁移表即使遗漏注解、直接调用 Mapper 也会拒绝。
当前 5 个已迁移方法由 `data-access-surface.golden` 冻结；全仓 21 个直接 JDBC
类由 `jdbc-bypass-surface.golden` 冻结为迁移债务，只能减少、不能新增。因此平台严格协议已启用，
但未完成模块迁移的 JDBC 接口仍不能视为行级数据权限已覆盖。迁移顺序见
[接口权限与数据权限全局保障方案](plans/data-permission-global-guard/FINAL_PLAN.md)。

若 Docker 使用 `OA_SECURITY_MODE=DEV`，调用方可以通过 `X-OA-User` 切换身份，该模式只能用于联调。
对外环境必须使用 JWT，并保持 `OA_IAM_ENFORCE=true`、`OA_DATA_SCOPE_STRICT=true`；JWT 模式下任一
开关为 false 会启动失败。否则接口和数据权限建立在可伪造身份上。可用
`docker inspect oa-app` 检查容器实际环境变量，不能仅根据 `.env.example` 判断运行状态。

### 「打卡显示成功但库里没有」

看 `GET /api/v1/attendance/admin/stats`：
- `deadLettered > 0` → 批量落库失败过，记录在 `oa_att.punch_dead_letter`，可人工补。
  幂等键在失败时会被撤销，所以用户重试能成功。
- `queueDepth` 持续很高 → 后台刷盘跟不上，调 `oa.attendance.punch.batch-size` / `flush-ms`。
- `degradedToSync > 0` → 队列曾经满过，说明峰值超出预期。

### 「发件箱堆积」

`GET /api/v1/flow/admin/status`：`pending` 持续增长且 `failed` 上升 → Kafka 不可达。
`dead > 0` → 某条消息重试 10 次仍失败，查 `oa_flow.oa_outbox.last_error`。

IAM 角色事件查看 `oa_iam.iam_outbox`，并监控 `oa_iam_outbox_pending`、
`oa_iam_outbox_dead`、`oa_iam_outbox_failed_total`。Kafka 不可用时角色变更仍会提交，事件保留并重试；
不得通过清表“解决”堆积。先核对事件 topic 是 `iam.role.changed.v1`，生产者使用 `OA_KAFKA`，而不是
`OA_WORKFLOW_KAFKA`。

### 「JWT 登录后仍 401 / WebSocket 连不上」

- 解码 token 检查 `iss`、`aud`、`sub`，它们必须分别匹配 `OA_JWT_ISSUER`、
  `OA_JWT_AUDIENCE` 与主体 UUID；只验签名不验 audience 不可接受。
- 先调用 `POST /api/v1/notify/ws-ticket`。WebSocket 只能使用一次性 ticket，重放、过期、错误 Origin
  都应握手失败；不要退化成 `/ws?token=<access_token>`。
- Casdoor token 可能超过 8KB；四服务已提高合法请求头上限。若前置网关仍返回 400，也要同步调整网关限制。
- 本地首次联调运行 `deploy/scripts/provision-oa-casdoor.sh`，确认回调包含 `:5473/:8404/:5474/:8405`。

### 「公告列表并发时连接池耗尽」

公告与已读状态必须是“两段查询”：先完成列表查询并释放连接，再按本页 ID 批量查回执。
不要在 `JdbcTemplate` 的行回调里逐行调用 `hasRead`；并发达到连接池大小时会形成所有请求
各占一个连接、同时等待第二个连接的自锁。可用全链路压测的“公告列表”场景回归。

### 「前端代理在后端重建后突然 502」

PC/移动 Nginx 必须通过 Docker DNS 动态解析上游，不能在启动时永久缓存容器 IP。
如果镜像已更新但旧容器仍持有配置，强制重建对应前端容器，再检查四个 `/api/v1` 代理。

---

## ★ 踩过的坑清单

按"再踩一次会浪费多少时间"排序。

### 环境与工具

1. **必须用 system maven，不能用 `mvnw`**。中台制品在 `/Users/liruijun/personal/repository`，
   `mvnw` 走 `~/.m2` 会解析不到。本项目**刻意不提供 mvnw**。
2. **仓库里的中台 jar 可能比中台源码旧**。遇到"方法找不到"先
   `javap` 一下仓库里的 class，必要时重装中台的 protocol+sdk 制品。
3. **本机 Testcontainers 跑不起来**（docker-java 找不到有效 Docker 环境）。
   集成验证一律写成 bash 冒烟脚本打运行中的 compose 容器。
4. **zsh 不做无引号变量分词**（bash 会）。`for x in $LIST` 会当成一个词 ——
   曾经因此建出一个名字含 15 个模块名的目录。循环要显式列举或用数组。
5. **bash 里 `$VAR` 后紧跟中文标点会被并进变量名**，一律写 `${VAR}`。
6. **中文不能直接拼进 URL 查询串**：请求根本到不了服务端，而响应常被 `>/dev/null` 吞掉，
   排查极费劲。冒烟脚本里 reason 一律用 ASCII。
7. **psql 无返回行时仍会把命令标签 `INSERT 0 0` 打到 stdout**。
   别用"输出是否为空"判断，直接数行数。

### JDK21 虚拟线程

8. **★ `Caffeine.get(key, loader)` 会 pin 载体线程**（底层 `computeIfAbsent` 持 synchronized 锁
   执行 loader，loader 里做 JDBC 就 pin）。实测吞吐 555 QPS → 17 QPS。
   一律改 **get-then-put**。**新引入任何在 synchronized 里做 I/O 的组件，都要重跑
   `Phase0PinningProbeTest`** —— Phase 0 的"不 pin"结论只覆盖当时的组件。

### PostgreSQL

9. **前缀 LIKE 必须建 `text_pattern_ops` 索引**（非 C locale 下普通 btree 不走前缀匹配）。
10. **小表上 Seq Scan 是正确计划**，不能用它判断索引没建对。
    验证方法：`SET enable_seqscan=off` 后看 Index Cond 有没有被改写成 `~>=~` / `~<~` 范围扫描。
11. **jsonb 列不接受 varchar 参数**，要 TypeHandler（见 ADR-0017）。
12. **`#{param} IS NULL` 让 PG 推断不出参数类型**，整条查询报
    `could not determine data type of parameter $1`，接口 500、字段全 null，
    极易被误读成"数据为空"。用动态 SQL 把条件整段去掉。
13. **同日 `from == to` 的区间是合法的**（建错部门当天撤销、上午入职下午调岗），
    时间区间约束要用 `>=` 而不是 `>`。

### MyBatis / Spring

14. **`Map<String,Object>` 接结果集是陷阱**：PG 把列名转小写、MyBatis 又按
    `mapUnderscoreToCamelCase` 改键名，两层叠加后 `map.get("x_y")` 写什么全靠猜，
    猜错静默返回 null。**一律用类型化 DTO**（本项目踩过三次）。
15. **`@Transactional` 标在 Mapper 接口上无效**（MyBatis 代理，不走 Spring 事务代理）。
16. **`@MapperScan` 通配只扫 `..infrastructure.mapper`**，嵌在别处的 mapper 接口扫不到。
17. **数据权限拦截器必须排在分页之前**，否则 count 语句绕过数据权限。
18. **`@TransactionalEventListener` 要加 `fallbackExecution = true`**，
    否则无事务上下文发布的事件被静默丢弃。
19. **参与 ObjectMapper 构造的 Bean 不能直接依赖需要 ObjectMapper 的 Bean**，
    会形成循环依赖，用 `ObjectProvider` 惰性拿。
20. **`ApplicationRunner` 跑在 Web 服务器启动之后**：这段窗口里已可能有请求把空快照缓存住。
    初始化里写了数据就必须连带做本地失效。

### 测试与度量

21. **ArchUnit 测试不能加 `DO_NOT_INCLUDE_JARS`**：其它模块正是 jar 形式在 classpath 上，
    加了等于所有跨模块规则空转。要有一条"确实扫到了业务模块的类"的前置断言。
22. **压测的延迟要看并发**：高并发下的 P99 ≈ 并发数 ÷ 吞吐（Little 定律），
    量的是客户端排队而不是服务端能力。测服务端真实处理时间要把并发压到不排队的水平。
23. **冒烟脚本要清 Redis，不只是清表**：上一轮的幂等键会让这一轮全部被判成"已处理"。
24. **重装数据后授权会"复活"**：`TRUNCATE ... RESTART IDENTITY` 让 user id 完全重现，
    旧授权被同名新用户继承。
25. **JDBC 行回调里不能做同池嵌套查询**：公告列表曾在每一行查询已读状态；并发等于池大小时
    所有线程各占一个连接再等第二个，最终全池自锁。先物化结果、释放连接，再批量查关联状态。
26. **并发上限要原子预留**：WebSocket 会话数用 `get` 后 `increment` 会在并发握手下越过上限；
    必须用 CAS 先占名额，注册失败再归还。
27. **E2E 不能只等 Toast**：CORS/500 的错误 Toast 也可能让“出现提示”断言通过；写操作必须同时
    等待成功 HTTP response 并核对后端状态。
28. **压测参数名本身属于契约**：组织树使用 `maxDepth` 而不是 `depth`。写错会静默返回整棵万人树，
    把 16KB 响应测成约 610KB，性能结论随即失真。

---

## 发布前质量关卡

```bash
mvn -B test
bash deploy/scripts/phase3-console-smoke.sh
bash deploy/scripts/phase4-jwt-ws-smoke.sh
bash deploy/scripts/phase5-mobile-smoke.sh
OA_PHASE9_APP_BASE=http://127.0.0.1:18400 bash deploy/scripts/phase9-iam-policy-smoke.sh
OA_NOTIFY_BASE=http://127.0.0.1:8401 OA_ADMIN=seed-user-1 \
  java deploy/scripts/FullChainLoadTest.java http://127.0.0.1:18400 1000 20
```

Phase 3 覆盖 PC 契约、当前 107 条单测、50 条浏览器 E2E、镜像与代理；Phase 4 覆盖真实 Casdoor、
四服务 JWT 与一次性 WS ticket；Phase 5 覆盖移动四主流程、390/320px、镜像与代理。
全链路压测必须 0 失败且每个场景 P99 低于程序内预算。详细基线见交付 QA 报告。

### 在线角色管理发布与回退

1. 发布前备份数据库并完成后端、PC 契约/测试/构建门禁。V16 是附加迁移，会从既有一跳闭包回填
   `role_inherit_edge`，先发布 `oa-app` 并确认 Flyway V16 成功，再发布 `oa-console`。
2. 发布后核对 9 个 `/api/v1/iam/role-admin` OpenAPI 路径、无 token 401、管理账号角色列表，以及
   `role_inherit_edge` / `role_inherit` 数量和 `oa_perm_mismatch_total`。
3. 应用异常时可回退前后端镜像；旧代码继续读取 `role_inherit`。保留 V16 新表，不执行 `DROP TABLE`。
   已产生的角色业务变更按审计记录做反向管理操作，不能用数据库回滚覆盖后续授权数据。

### V18 租户权限纪元与 IAM 事件发布

1. 发布前在 OA Kafka 集群预创建 `iam.role.changed.v1`，分区数、副本数、保留期按平台业务事件规范设置；
   生产环境不得依赖 broker 自动建主题。本地 Compose 的自动建主题只用于开发与验收。
2. 第一阶段保持所有服务 `OA_IAM_LEGACY_EPOCH_FENCE_ENABLED=true`，先部署 `oa-app` 使 Flyway V18
   创建并回填 `tenant_perm_epoch` 和 `iam_outbox`，再滚动升级其它内嵌判权引擎的服务。
3. 混合版本阶段新代码同时推进租户 epoch 与旧全局 epoch；新节点也读取两者最大值。因此旧节点写入、
   Pub/Sub 丢失时仍能在 1 秒内收敛，代价是这一阶段仍可能全局失效。
4. 确认所有节点均为 V18+、`oa_perm_mismatch_total=0`、IAM Outbox 无异常堆积后，将所有服务的
   `OA_IAM_LEGACY_EPOCH_FENCE_ENABLED=false` 并滚动重启，正式启用租户级失效范围。
5. 回退旧镜像时重新设为 `true`。V18 是附加迁移，保留两张新表；旧代码继续读取 `perm_epoch`，
   不得删除 `tenant_perm_epoch` 或 `iam_outbox`。

### 权限加固与 JIT 四眼审批发布

1. 先部署后端并确认 Flyway V17、报表 V83 成功，再部署控制台；不要删除既有 Redis 数据，快照 codec
   版本不兼容时会 fail-safe 重建。
2. 使用两个不同管理员账号验证：员工提交 JIT 申请后权限未变化，申请人自批返回 403，第二人批准后
   `elevation_request.grant_id` 与 `grant_record.approval_instance_id` 双向可定位。
3. 对 CUSTOM 角色执行区域内成功、区域外空列表/403 的 IDOR 烟测，并观察
   `oa_data_scope_missing_total`、`oa_data_scope_alias_mismatch_total` 与拒绝审计。
4. 应用可回退到旧镜像；V17/V83 均为向前附加迁移，保留表和视图。已批准的临时授权按正常撤权接口回收。

### USER_GROUP / ABAC 发布与回滚

1. V14 只新增组表并增强 `permission_condition`；先部署迁移和代码，保持
   `OA_IAM_ABAC_ENABLED=false`。
2. 运行 `OA_PHASE9_APP_BASE=http://127.0.0.1:8400 bash deploy/scripts/phase9-iam-policy-smoke.sh`。
   脚本会回收授权/成员/条件，只保留一条禁用的 QA 用户组生命周期记录。
3. 在 PC“权限策略 → ABAC 条件”配置并审查策略，测试环境设
   `OA_IAM_ABAC_ENABLED=true` 后重启。开启状态会进入快照协议，旧的 ABAC-off L2 快照不会复用。
4. 拒绝或错误突增时先关闭该环境变量并重启，恢复历史 RBAC；无需回滚 V14。用户组异常可逐组禁用。

表达式只允许 `#user`、`#args`、`#p0/#a0` 和可用的方法参数名，以及比较、布尔和基础算术。
禁止 `T(...)`、`new`、`@bean`、方法调用、赋值和 Class/Runtime 访问。求值错误按拒绝处理，日志不记录参数值。
授权维度、全部用户属性、条件组合规则和管理端编辑步骤见 [ABAC 授权与管理指南](ABAC.md)。
RBAC、接口权限、数据权限、字段权限和当前迁移边界见
[权限体系与全局保障指南](AUTHORIZATION.md)。

---

## 生产上线前必须做的

- [ ] `OA_CRYPTO_DATA_KEY` 注入真实密钥（`openssl rand -base64 32`），
      否则敏感字段用的是仓库里写死的开发默认密钥
- [ ] 为生产域名配置 Casdoor `issuer` / `audience` / SPA 回调（代码与 compose 已默认 JWT）
- [ ] `OA_IAM_ENFORCE=true`（默认已是）
- [ ] `OA_DATA_SCOPE_STRICT=true`（默认已是；JWT 模式关闭会启动失败）
- [ ] `oa_data_scope_missing_total` / `oa_data_scope_alias_mismatch_total` 告警已配置为任意增长即触发
- [ ] `OA_ORG_SEED_ENABLED=false`（万人级装载接口必须关）
- [ ] `OA_BOOTSTRAP_ADMIN` 初始化完成后清空
- [ ] 影子校验跑满 2 周且 `oa_perm_mismatch_total` 恒为 0 后再考虑关闭
- [ ] V18 全节点升级完成后统一关闭 `OA_IAM_LEGACY_EPOCH_FENCE_ENABLED`，并监控 IAM Outbox 指标
- [ ] 配置生产调度触发 `oa-job-service` 的考勤日结与 `punch_record` 分区滚动
- [x] 越权 / IDOR 渗透用例（Phase 8）
- [ ] 在目标生产容量与真实网关/TLS 下复跑 JWT、WS 和全链路压测
