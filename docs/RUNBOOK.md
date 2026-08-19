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
bash deploy/build-images.sh
docker compose -p oa-platform -f deploy/docker-compose.yml --profile apps up -d

# 收尾
docker compose -p oa-platform -f deploy/docker-compose.yml --profile apps down
pkill -f 'oa-app-.*\.jar'
```

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
| 发件箱 | `GET /api/v1/flow/admin/status` | `dead: 0`，`pending` 不持续增长 |
| 打卡链路 | `GET /api/v1/attendance/admin/stats` | `deadLettered: 0`，`duplicates: 0` |

---

## 排障

### 「改了权限怎么没生效」

先看 `GET /api/v1/iam/admin/explain?userId=…&permCode=…`：

- `consistent: false` → 缓存与真值漂了，看 `cachedEpoch` vs `truthEpoch`；
  纪元没推进说明写侧漏了失效调用。
- `consistent: true` 但结果不符预期 → 问题在授权本身，
  查 `GET /api/v1/iam/grants?subjectType=USER&subjectId=…`。
- `requiresElevation: true` 且 `currentlyElevated: false` → 高危操作需要先申请 JIT 提权。

**收权最长 1 秒生效**（本地纪元缓存窗口），**给个人加权在本节点立即生效**、
跨节点靠失效总线（毫秒）或 TTL（5 分钟）兜底。

### 「查出来的数据比预期多/少」

数据权限只在标了 `@DataScope` 的服务方法上生效，且**别名必须与 SQL 里的表别名一致** ——
对不上就等于没有过滤。用 `GET /api/v1/me/permissions` 看 `dataScope` 与 `scopePrefixes`，
它们就是拼进 WHERE 的那些前缀。

### 「打卡显示成功但库里没有」

看 `GET /api/v1/attendance/admin/stats`：
- `deadLettered > 0` → 批量落库失败过，记录在 `oa_att.punch_dead_letter`，可人工补。
  幂等键在失败时会被撤销，所以用户重试能成功。
- `queueDepth` 持续很高 → 后台刷盘跟不上，调 `oa.attendance.punch.batch-size` / `flush-ms`。
- `degradedToSync > 0` → 队列曾经满过，说明峰值超出预期。

### 「发件箱堆积」

`GET /api/v1/flow/admin/status`：`pending` 持续增长且 `failed` 上升 → Kafka 不可达。
`dead > 0` → 某条消息重试 10 次仍失败，查 `oa_flow.oa_outbox.last_error`。

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

---

## 生产上线前必须做的

- [ ] `OA_CRYPTO_DATA_KEY` 注入真实密钥（`openssl rand -base64 32`），
      否则敏感字段用的是仓库里写死的开发默认密钥
- [ ] `oa.security.mode` 改 `JWT` 并配 Casdoor `issuer-uri`
- [ ] `OA_IAM_ENFORCE=true`（默认已是）
- [ ] `OA_ORG_SEED_ENABLED=false`（万人级装载接口必须关）
- [ ] `OA_BOOTSTRAP_ADMIN` 初始化完成后清空
- [ ] 影子校验跑满 2 周且 `oa_perm_mismatch_total` 恒为 0 后再考虑关闭
- [ ] `oa-job-service` 接管考勤日结与 `punch_record` 分区滚动
- [ ] 越权 / IDOR 渗透用例（Phase 8）
