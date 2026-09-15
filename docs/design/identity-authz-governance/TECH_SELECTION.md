# TECH_SELECTION

Brownfield：只对**新增或会改变运行时**的项做选型。已锁定项记为约束。

| 决策 | 当前/约束 | 候选 | 选定 | 选择理由 | 否决原因 | 版本/兼容 |
|---|---|---|---|---|---|---|
| 语言/运行时 | JDK 21 已运行 | 保持 / 升级 | **保持 JDK 21** | 硬约束与仓库一致 | 无升级收益 | 21 |
| 服务框架 | Boot 3.3.5 | 保持 / 3.4+ | **保持 3.3.5** | 禁止为升级重写 | 无兼容证据要求升 | 3.3.5 |
| Spring Cloud / Alibaba | 无 | 引入 / 不引入 | **不引入** | 4 个 JVM 进程 compose 直连已够 | Nacos/Feign 无服务发现问题 | — |
| 服务发现 | compose DNS + nginx | Nacos / DNS | **DNS/nginx** | ADR-0002 已否完整微服务 | Nacos 无对象 | — |
| 配置中心 | env + YAML | Apollo / Nacos config / env | **env** | Apollo 与 Nacos 不得抢同一职责；动态项已用 env | 无多环境配置爆炸证据 | — |
| API 入口 | console/mobile nginx | SCG / nginx | **nginx** | 已做路由/WS；PDP 必须在服务内 | SCG 不能替代内部授权 | nginx 1.27 |
| 服务间调用 | 模块内 Java / workflow SDK | Feign / SDK | **保持进程内 + 现有 SDK** | 无新同步长链 | Feign 无新下游 HTTP 服务 | — |
| 主数据存储 | PostgreSQL 16 | PG / MySQL | **PostgreSQL** | org_path `text_pattern_ops`、pg_trgm、分区、jsonb 已绑定；迁 MySQL 是独立高风险项目 | 硬约束 MySQL 作为演进项，见 D-GOV-001 | 16 |
| 迁移 | Flyway | 保持 | **Flyway** | 已运行；IAM 新版本从 V110 起 | 改已执行迁移会 checksum 失败 | out-of-order |
| ORM/SQL | MP 3.5.5 + 注解 SQL | XML / 注解 | **新增必须 Mapper XML** | 用户硬约束 + 实施规范 | 不回刷旧 Mapper | 3.5.5 |
| 缓存 | 已有 L1+L2 权限快照 | 保持 / 扩大 | **权限快照保持；Identity 目录默认不缓存** | Identity 非热路径 | 禁止无差别加缓存；禁止 Caffeine loader pin | Caffeine+Redis7 |
| 搜索 | pg_trgm | 保持 / ES | **DB ILIKE/trgm** | 身份目录规模远小于通讯录 | ES 无相关性需求 | — |
| 消息 | Kafka 3.8 + Outbox | 保持 / RocketMQ / Rabbit | **Kafka** | 已有总线与 outbox | 换 MQ 无问题可证明 | 3.8.0 |
| Outbox/Inbox | 流程+角色已有 | 按问题启用 | **仅跨进程副作用时用** | 硬约束 §13 | 禁止所有写都 Outbox | 现表 |
| 调度 | Spring @Scheduled + job-service | 保持 / PowerJob | **保持** | 到期回收已有 | PowerJob 未使用 | — |
| 认证 IdP | Casdoor | 保持 | **Casdoor** | ADR-0001 | 自建登录 | 外部 |
| 主 PDP | PermissionEngine | 保持并门面化 / SpiceDB / OPA | **本地 DecisionEngine 门面** | 热路径零远程 | SpiceDB 远程 check 扛不住万人页面权限；OPA 新运维面 | 现引擎 |
| 图存储 | 无 | PG 边表 / Neo4j | **PostgreSQL 边表** | 一期 1–2 跳 | 图库无复杂遍历证据 | — |
| 规则引擎 | ABAC SpEL | SpEL / Drools / CEL | **保持受限 SpEL，条件树后续可替换** | 已 fail-closed | Drools 运维重 | 现 AbacEvaluator |
| 可观测 | actuator + 日志 + traceId 字段 | 保持 / OTel | **保持并加授权计数器** | Tracing 平台未建设 | 不新上 Jaeger | micrometer 已有 |
| 前端 | React18 + antd5 + Vite | 保持 | **保持** | Brownfield IA 演进 | 不换栈、不第二套组件 | 见 FRONTEND |

## Complexity Budget（否决项摘要）

1. **MySQL**：不解决当前问题；会破坏路径索引、中文检索、分区与 jsonb TypeHandler。更简单方案=继续 PG。
2. **Nacos**：当前没有服务发现故障；compose DNS 已够。
3. **Apollo**：动态项少，env 重启可接受；与「Nacos 不兼配置中心」同时引入两套配置中心违反硬约束 §5。
4. **Spring Cloud Gateway**：nginx 已承担 Route；Authorization 不能只放网关。
5. **OpenFeign**：无新 HTTP 服务间调用链。
6. **图数据库 / Drools / 新 MQ**：无对应查询或编排问题。
