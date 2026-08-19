# oa-platform 工作约定（给 Claude Code 读）

## 构建
**必须用 system maven**，不要用 `./mvnw`（本项目刻意没有 mvnw）：
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin:$PATH"
```
中台制品 `com.lrj.workflow` / `com.lrj.authz` 在 `/Users/liruijun/personal/repository`，不在 `~/.m2`。
仓库里的中台 jar 可能比中台源码旧；遇到"方法找不到"先 `javap` 一下，必要时重装其 protocol+sdk。

## 权威文档
- 架构 `docs/ARCHITECTURE.md` · 决策 `docs/ADR.md` · 运维与踩坑 `docs/RUNBOOK.md` · 接口 `docs/API.md`
- 规划与**权威进度** `../docs/plans/oa-platform-0819-1721/`（IMPLEMENTATION_PROGRESS.md 为准）

## 硬约束（违反会被构建或运行期拦下）
1. 新增 REST handler **必须**带 `@RequiresPerm("oa:模块:动作")` 或 `@PublicApi(reason="...")`，
   否则 `ControllerPermissionCoverageTest` 让构建失败，运行期 fail-closed 拦截器也会拒绝。
   **同时必须在对应模块的迁移里加权限点** —— 目录里没有的 code 一律判为拒绝。
2. 跨模块只能依赖对方 `..api..` 包，不许注入对方 Mapper/Entity（ArchUnit 会拦）。
   Controller 也不许直连 Mapper，必须走服务层（`@DataScope` 在那一层）。
3. 业务单据表必须冗余 `org_id` + `org_path`；`org_path` 索引必须是 `text_pattern_ops`。
4. 数据权限一律走 `org_path LIKE '前缀%'`，**不许**展开成 `org_id IN (大列表)`；
   算不出范围时生成 `1 = 0`（一行都不给），**绝不能**退化成"不加条件"。
5. 主体标识统一用 Casdoor `sub`（UUID 字符串），**不许**用自增 Long。
6. 对 `workflow-platform` / `auth-platform` 的改动一律**只增不改**。
7. SpiceDB `:8543` 写 schema 是**全量替换**，加 `oa.zed` 必须与 knowledge/his/recsys/risk 合并后再写。
8. Mapper 一律放 `..infrastructure.mapper` 包（`@MapperScan` 只扫这个通配）。
9. 结果集**一律用类型化 DTO**，不用 `Map<String,Object>`（PG 转小写 + MyBatis 转驼峰，两层叠加后取值全靠猜）。

## ★★★ 引入新组件前必读

**新引入任何在 `synchronized` 块里做 I/O 的组件，都要重跑 `Phase0PinningProbeTest`。**
JDK21 虚拟线程在 `synchronized` 里阻塞会 pin 载体线程。Phase 0 的"HikariCP 不 pin"结论
**只覆盖当时的组件**。Phase 5 就栽过：`Caffeine.get(key, loader)` 底层 `computeIfAbsent`
持锁执行 loader，加了缓存后吞吐从 555 QPS 塌到 17 QPS。缓存加载一律 **get-then-put**。

## ★ 静默失败清单（这些都不报错，只是悄悄不干活）

1. **`spring-boot:repackage` 增量构建会保留旧的嵌套依赖 jar**。给某个 library 模块加了
   Flyway 迁移，`mvn install` 整个 reactor 都成功，fat jar 里却还是旧的那份 —— 表现为
   "迁移文件明明在，就是没执行"，日志只说 `Successfully validated 10 migrations`。
   **改了任何 `src/main/resources` 下的东西，就得 `clean install`。**
2. **分区表上的唯一索引必须包含分区键**，于是 `(user_id, dedup_key, created_at)` 这种
   "唯一"等于"同一时刻才算重复"，完全不去重。跨时间的幂等键必须放在**不分区的小表**上。
3. **中台 SDK 的 `workflow.client.enabled` 默认 false**，注入 `NoopWorkflowClient`：
   查询返回空列表、不抛异常。网关会一边宣称 `remote()=true` 一边一条待办也查不到。
   已在 `RemoteWorkflowGateway` 构造器 fail-fast。
4. **两套 compose 各有一套 Kafka**（OA 39092 / workflow 29092），投错总线时 `send()`
   照常返回 offset，消费方永远收不到。按主题前缀分流，见 `WorkflowCommandKafkaConfig`。
5. **ArchUnit 扫的是 classpath**：oa-app 不依赖三个独立服务，所以权限覆盖检查一度
   **一个独立服务的 Controller 都没检查过**。规则已抽到 `oa-security` 的 test-jar，
   规则自身先断言"确实扫到了 `@RestController`"——扫不到东西的检查会永远通过。
6. **`GlobalExceptionHandler` 只在主应用里**时，独立服务的权限拒绝会变成
   **HTTP 500 + 空 body**。已移到 `oa-common`，四个可部署单元共用。
7. **macOS 的 BSD `date` 不支持 `%3N`**，且不报错、原样吐出字母 `N`，
   于是 `|| fallback` 根本不触发。冒烟里取毫秒一律用 `python3`。
8. **`@DataScope` 只对走 MyBatis 的查询生效**。标在用 JdbcTemplate 手写 SQL 的方法上，
   切面照常设上下文、拦截器永远不被调用 —— 注解明晃晃写着，实际返回全量数据。
   已在 `DataScopeAspect` 加"设了却没人消费"的告警；但正确做法是需要数据权限就用 Mapper。
9. **裸 SQL 改 `grant_record` 会绕过失效协议**：epoch 不 bump，L1(进程内)+L2(Redis) 里的
   旧快照继续放行。清 Redis 也不够 —— **必须先 FLUSHDB 再重启**，反过来 L1 已从旧 L2 装载好了。
   冒烟里授权/收权一律走 API。（本轮排查最久的第二个，靠 `/iam/admin/explain` 的
   `consistent=false` 才定位到。）
10. **PG 自带分词切不了中文**：`to_tsvector('simple','关于国庆放假的通知')` 是一个整 token，
    搜"放假"永远 0 行，接口返回 200 + 空数组，像"确实没这份文件"。已改 `pg_trgm` + ILIKE。
11. **改已执行过的迁移 = Flyway checksum 不匹配、启动直接失败**。这是它该有的行为，
    别去 repair 绕过；兼容逻辑写进新版本号的迁移里。
12. **冒烟脚本里无参 `wait` 会等上你用 `&` 起的应用进程**（它永不退出）。
    并发请求一律用 `xargs -P`，别用 `cmd & ... wait`。

## 本机现实
- **Testcontainers 跑不起来** → 集成验证一律写成 bash 冒烟脚本打运行中的 compose 容器。
- compose 起之前先 `docker compose -p oa-platform down --remove-orphans`（清 docker-proxy 残留）。
- **zsh 不做无引号变量分词**：`for x in $LIST` 会当成一个词，循环要显式列举。
- bash 里 `$VAR` 后紧跟中文标点会被并进变量名，一律写 `${VAR}`；
  **中文不要拼进 URL 查询串**（请求到不了服务端，且响应常被丢弃，极难排查）。
- psql 无返回行时仍会把 `INSERT 0 0` 打到 stdout，别用"输出是否为空"做判断。
- 端口 9092 属 langchain4j、29092/25432 属 workflow、15432 属 auth，**别动别人的容器**。

## 冒烟脚本
```
deploy/scripts/phase0-smoke.sh              基建 + 两道硬门禁            14 断言
deploy/scripts/phase1-org-smoke.sh          组织域                      28 断言
deploy/scripts/phase2-perm-smoke.sh         权限域                      32 断言
deploy/scripts/phase4-flow-smoke.sh         审批底座                    25 断言
deploy/scripts/phase4b-remote-smoke.sh      审批·接真中台（集成）        28 断言
deploy/scripts/phase5-attendance-smoke.sh   考勤 + 早高峰压测           19 断言
deploy/scripts/phase6-notify-smoke.sh       通知 + 万人公告 + 位图回执   28 断言
deploy/scripts/phase7-doc-admin-smoke.sh    公文/知识库/会议室排他约束   45 断言
deploy/scripts/WsProbe.java                 长连探针（curl 不会说 WebSocket）
```
改完代码至少跑相关阶段的那个。压测的延迟数字要看并发（Little 定律），
高并发下的 P99 量的是客户端排队，不是服务端能力。

## 前端
PC `oa-console` 与移动端 `oa-mobile` 在**动手实现前必须先走 `/frontend-plan`**
（auth-console / workflow-console 都是这么做的）。
