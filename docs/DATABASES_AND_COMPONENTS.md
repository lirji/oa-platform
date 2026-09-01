# 数据库与组件清单

本文汇总 `oa-platform` 当前代码和本地部署配置中实际使用的数据库、基础设施、主要技术组件与内部模块。清单以构建依赖、`application.yml`、Docker Compose、数据库迁移和实际代码调用为准；历史规划中出现、但当前未启用或尚未接通的组件会单独标注。

## 1. 总览

| 类别 | 组件 | 当前定位 |
|---|---|---|
| 关系型数据库 | PostgreSQL 16 | 全部结构化业务数据的权威存储；一个物理数据库按业务 schema 隔离 |
| 缓存与协调 | Redis 7 | 权限 L2 缓存、跨节点缓存失效、打卡幂等、WebSocket 一次性票据 |
| 消息队列 | Apache Kafka 3.8.0 | 审批事务发件箱投递，以及向 workflow-platform 发送流程命令 |
| 对象存储 | MinIO | 文件二进制存储；文件元数据仍保存在 PostgreSQL |
| 身份认证 | Casdoor（外部复用） | OIDC 授权码 + PKCE 登录、JWT 签发与 JWK 校验 |
| 流程引擎 | workflow-platform（外部复用） | 审批流程部署、发起、查询和办理；默认使用远程模式 |
| 后端基础 | Java 21、Spring Boot 3.3.5 | Maven 多模块后端与 4 个可部署单元 |
| 前端基础 | React 18、TypeScript、Vite 5 | PC 管控台与移动端 H5 |

本地基础设施由 [`deploy/docker-compose.yml`](../deploy/docker-compose.yml) 提供。默认执行 `docker compose up` 只启动 PostgreSQL、Redis、Kafka 和 MinIO；应用服务位于 `apps` profile。

## 2. 数据库与存储

### 2.1 PostgreSQL

项目当前只使用一套关系型数据库：PostgreSQL。默认物理数据库名为 `oa`，业务通过 schema 隔离，没有为每个模块建立独立数据库。

| Schema | 主要归属 | 主要数据 | 迁移来源 |
|---|---|---|---|
| `oa_org` | `oa-org` | 组织单元、闭包表、员工、岗位、任职、汇报线、目录同步 | `oa-org/src/main/resources/db/migration/` |
| `oa_iam` | `oa-iam` | 权限点、角色、授权、委托、权限版本、用户组、ABAC 条件 | `oa-iam/src/main/resources/db/migration/` |
| `oa_flow` | `oa-flow` | 表单模板、审批实例、待办读模型、事务发件箱/收件箱、请假 | `oa-flow/src/main/resources/db/migration/` |
| `oa_att` | `oa-attendance` | 打卡、死信、考勤日结、班次 | `oa-attendance/src/main/resources/db/migration/` |
| `oa_doc` | `oa-doc` | 公文、知识库目录与文档、分享关系 | `oa-doc/src/main/resources/db/migration/` |
| `oa_admin` | `oa-admin-biz` | 会议室与预订、资产、物资、车辆、访客 | `oa-admin-biz/src/main/resources/db/migration/` |
| `oa_sys` | 公共、报表、文件、跑批 | 号段、审计、字典、系统配置、文件元数据、任务执行记录 | `oa-common`、`oa-report`、`oa-file-service`、`oa-job-service` 的迁移目录 |
| `oa_notify` | `oa-notify-service` | 站内信、公告、已读位图、接收人索引、渠道日志、去重记录 | `oa-notify-service/src/main/resources/db/migration/notify/` |

`oa_iam` 中与在线角色管理直接相关的权威关系如下：

| 表 | 职责 | 写入约束 |
|---|---|---|
| `role` | 角色基本信息、类型、状态、默认数据范围与乐观锁版本 | 在线只创建 `CUSTOM`；编码不可变；删除为软删除 |
| `role_permission` | 角色直接拥有的权限点 | 更新矩阵后推进租户权限纪元 |
| `role_inherit_edge` | 管理员配置的直接继承边，是继承关系的写侧真值 | V16 新增；租户锁内校验防环 |
| `role_inherit` | 从直接边重建的 ACTIVE-only 传递闭包，服务判权读路径 | 不作为管理端直接编辑对象 |
| `grant_record` | 用户、组织、岗位、用户组到角色的授权记录及历史 | 非 ACTIVE 角色禁止新增授权 |
| `tenant_perm_epoch` | 每租户权限快照失效版本 | V18 新增；数据库真值，Redis 只做加速通知 |
| `iam_outbox` | 角色领域事件事务发件箱 | 与角色真值和 epoch 同事务；Kafka 失败异步重试 |

V16 会从已有 `role_inherit.distance = 1` 关系回填直接边，不删除或改写既有业务表。部署时应先让
`oa-app` 完成 Flyway，再检查 `flyway_schema_history` 中 V16 成功；回退应用版本时保留附加表，
不要通过删表回滚已经发生的角色配置。

数据库迁移统一使用 Flyway，但按部署单元维护迁移历史：

- `oa-app` 扫描各业务模块的 `classpath:db/migration`，使用主历史表，并开启 `out-of-order` 以支持模块化版本号段。
- `oa-notify-service` 使用 `flyway_schema_history_notify`。
- `oa-file-service` 使用 `flyway_schema_history_file`。
- `oa-job-service` 使用 `flyway_schema_history_job`。

已启用的 PostgreSQL 扩展：

- `btree_gist`：支持会议室时间段排他约束。
- `pgcrypto`：提供数据库侧加密/随机标识能力。
- `pg_trgm`：支持公文和知识库的中文/模糊检索 GIN 索引。

项目的全文与模糊检索当前直接使用 PostgreSQL，不依赖 Elasticsearch。

### 2.2 Redis

Redis 不是业务数据的权威来源，主要承担可丢失、可回源或有数据库兜底的高速状态：

| 用途 | 使用模块 | 说明 |
|---|---|---|
| 权限快照 L2 缓存 | `oa-iam` | L1 Caffeine 未命中后读取 Redis，再未命中时从 PostgreSQL 重算 |
| 缓存失效广播 | `oa-common`、`oa-org`、`oa-iam` | 通过 `oa:cache:invalidate` Pub/Sub 频道通知其他节点 |
| 打卡幂等 | `oa-attendance` | `SETNX` 作为第一道闸门；PostgreSQL 唯一约束仍负责最终一致性 |
| WebSocket 一次性票据 | `oa-notify-service` | 短 TTL、原子取用即删；Redis 故障时握手 fail-closed |

本地 Compose 使用 `redis:7-alpine`，关闭 AOF，限制 512 MB，并采用 `allkeys-lru`。这些是本地开发参数，不应直接视为生产配置。

### 2.3 MinIO

MinIO 仅由 `oa-file-service` 使用：

- 文件内容存放在默认 bucket `oa-files`。
- 文件名、大小、哈希、归属人、业务关联和组织路径等元数据保存在 PostgreSQL 的 `oa_sys.file_object`。
- 下载支持短时预签名 URL，默认有效期 300 秒。
- Java 客户端版本为 7.1.0；本地 Compose 的 MinIO 服务镜像当前使用 `minio/minio:latest`，尚未固定服务端版本。

## 3. 中间件与外部平台

### 3.1 Kafka

Kafka 生产链路包括：`oa-flow` 通过 `oa_flow.oa_outbox` 投递流程命令，IAM 通过
`oa_iam.iam_outbox` 投递 `iam.role.changed.v1`。两者都先同事务写 Outbox，再由后台发布器发送，
避免业务提交成功但事件丢失。权限 L1 失效仍使用 Redis Pub/Sub，Kafka 不是判权正确性边界。

平台存在两类 Kafka 地址：

- `OA_KAFKA`：OA 自己的事件总线，默认 `localhost:39092`。
- `OA_WORKFLOW_KAFKA`：workflow-platform 的命令总线；配置后，`workflow.command.*` 主题会发往该总线。

`oa-notify-service` 和 `oa-job-service` 当前包含 Spring Kafka 依赖及连接配置，但代码中没有活动的 Kafka 生产者或监听器，属于预留接入，不计作当前消息链路。

本地 Compose 使用 Apache Kafka 3.8.0、单节点 KRaft 和自动建主题，仅适合开发与验收。

### 3.2 Casdoor

Casdoor 不在本项目的 Compose 中，由外部认证平台复用，默认地址为 `http://localhost:8000`。

- PC 与移动端通过 `oidc-client-ts` / `react-oidc-context` 使用 OIDC 授权码 + PKCE。
- 后端通过 Spring Security OAuth2 Resource Server 校验 JWT 的 JWK、issuer 和可选 audience。
- 系统主体标识统一使用 Casdoor JWT 的 `sub`。

### 3.3 workflow-platform

`oa-flow` 通过 `workflow-platform-sdk` 与外部流程中台集成：

- 默认 `REMOTE` 模式，HTTP 基址为 `http://localhost:8300`。
- 流程发起命令通过事务发件箱和 Kafka 投递。
- 任务查询、办理和对账通过 SDK/HTTP 完成。
- `LOCAL` 模式只用于不依赖外部流程进程的本地测试替身。

### 3.4 当前未启用的可选组件

`oa-doc` 保留了 auth-platform / SpiceDB 的知识库对象授权适配入口，但 `oa.authz.spicedb.enabled` 默认是 `false`，且当前 `SpiceDbKbAuthorizer` 会明确拒绝调用。因此 SpiceDB 不是当前可运行环境的依赖；知识库授权实际使用 PostgreSQL 中的 `oa_doc.kb_share`。

## 4. 后端技术组件

| 组件 | 版本/来源 | 用途 |
|---|---|---|
| Java | 21 | 运行时，启用虚拟线程 |
| Spring Boot | 3.3.5 | Web、依赖装配、配置、Actuator 等基础能力 |
| Spring MVC / WebSocket | 随 Spring Boot | REST API；通知服务的浏览器实时推送 |
| Spring Security OAuth2 Resource Server | 随 Spring Boot | Casdoor JWT 校验和请求认证 |
| MyBatis-Plus | 3.5.5 | Mapper、分页和数据权限 SQL 拦截 |
| Spring JDBC | 随 Spring Boot | 复杂 SQL、批量操作及部分高性能读写路径 |
| PostgreSQL JDBC / HikariCP | 随 Spring Boot 管理 | 数据库驱动与连接池 |
| Flyway | 随 Spring Boot 管理 | Schema 与表结构版本迁移 |
| Spring Data Redis | 随 Spring Boot | Redis 缓存、原子操作与 Pub/Sub |
| Spring Kafka | 随 Spring Boot | 事务发件箱消息发布 |
| Caffeine | 3.1.8 | 权限快照、员工索引和 ABAC 表达式的进程内缓存 |
| RoaringBitmap | 1.3.0 | 权限位图和万人公告受众/已读回执压缩 |
| MinIO Java SDK | 7.1.0 | 文件上传、下载、删除与预签名地址 |
| springdoc-openapi | 2.6.0 | OpenAPI 文档与前端 TypeScript 契约源 |
| Actuator / Micrometer | 随 Spring Boot | 健康检查与应用指标；权限和数据范围代码通过 `MeterRegistry` 记录指标 |
| ArchUnit | 1.3.0（测试） | 模块边界、授权立场和数据访问架构门禁 |

应用配置中将 `prometheus` 列入 Actuator 暴露清单，但当前 POM 没有显式引入 Prometheus registry，Compose 也没有部署 Prometheus 或 Grafana。需要采集 Prometheus 格式指标时，应先补齐 registry 依赖并验证 `/actuator/prometheus` 端点实际存在。

## 5. 前端技术组件

| 组件 | PC `oa-console` | 移动端 `oa-mobile` | 用途 |
|---|---:|---:|---|
| React | 18.3.1 | 18.3.1 | UI 框架 |
| TypeScript | 5.6.3 | 5.6.3 | 类型系统与构建检查 |
| Vite | 5.4.10 | 5.4.10 | 开发服务器与构建 |
| Ant Design | 5.21.6 | — | PC 组件库 |
| Ant Design Mobile | — | 5.41.1 | 移动端组件库 |
| TanStack Query | 5.59.16 | 5.59.16 | 服务端状态、缓存和请求生命周期 |
| React Router | 6.27.0 | 6.27.0 | 路由与页面守卫 |
| Axios | 1.7.7 | 1.7.7 | HTTP 客户端 |
| oidc-client-ts / react-oidc-context | 3.5.0 / 3.3.1 | 3.5.0 / 3.3.1 | Casdoor OIDC 登录与 token 生命周期 |
| Zustand | 5.0.1 | — | PC 本地状态管理 |
| TanStack Virtual | 3.10.8 | — | PC 通讯录长列表虚拟化 |
| idb | 8.0.0 | — | PC 通讯录同步状态的 IndexedDB 封装 |
| Zod | 3.23.8 | — | PC 报表响应的运行时结构校验 |
| Vitest / Testing Library | 2.1.4 | 2.1.4 | 单元与组件测试 |
| Playwright | 1.48.0 | 1.48.0 | 浏览器端到端测试 |
| Nginx | 1.27-alpine | 1.27-alpine | 容器内静态资源服务与后端反向代理 |

PC 端还使用 `openapi-typescript` 7.13.0 将固定的 OpenAPI 快照生成 TypeScript 类型，并通过 `gen:api:check` 防止契约漂移。

## 6. 内部模块与部署单元

### 6.1 后端模块

| 类型 | 模块 | 职责 |
|---|---|---|
| 依赖管理 | `oa-dependencies` | 统一管理第三方库和中台 SDK 版本 |
| 公共内核 | `oa-common` | 返回模型、异常、租户上下文、加密、号段、JSONB TypeHandler、缓存失效总线 |
| 公共契约 | `oa-protocol` | 对外 DTO 与事件契约 |
| 安全端口 | `oa-security` | JWT、用户上下文和权限/数据范围/敏感字段注解及接口 |
| 业务域 | `oa-org` | 组织、员工、岗位、任职、汇报线和组织树缓存 |
| 业务域 | `oa-iam` | 在线角色与继承治理、RBAC、用户组、受限 ABAC、权限快照和数据权限引擎 |
| 业务域 | `oa-flow` | 表单、审批、事务发件箱、待办读模型和流程中台集成 |
| 业务域 | `oa-attendance` | 排班、打卡削峰和考勤日结 |
| 业务域 | `oa-doc` | 公文与知识库 |
| 业务域 | `oa-admin-biz` | 会议室、资产、物资、车辆和访客 |
| 业务域 | `oa-report` | 报表、审计、字典和系统配置 |
| 部署单元 | `oa-app` | 装配全部业务域的主应用，端口 8400 |
| 部署单元 | `oa-notify-service` | 站内信、公告、多渠道和 WebSocket，端口 8401 |
| 部署单元 | `oa-file-service` | 文件元数据与 MinIO I/O，端口 8402 |
| 部署单元 | `oa-job-service` | 跑批、分片调度和授权到期回收，端口 8403 |

### 6.2 前端模块

| 模块 | 职责 | 开发/容器端口 |
|---|---|---|
| `oa-console` | PC 管控台 | 5473 / 8404 |
| `oa-mobile` | 员工移动端 H5 | 5474 / 8405 |

## 7. 默认端口与配置入口

| 组件/服务 | 默认宿主端口 | 主要配置 |
|---|---:|---|
| PostgreSQL | 35432 | 应用：`OA_PG_HOST`、`OA_PG_PORT`、`OA_PG_DB`、`OA_PG_USER`、`OA_PG_PASSWORD`；Compose 映射：`OA_PG_HOST_PORT` |
| Redis | 36379 | 应用：`OA_REDIS_HOST`、`OA_REDIS_PORT`；Compose 映射：`OA_REDIS_HOST_PORT` |
| Kafka | 39092 | 应用：`OA_KAFKA`；Compose 映射：`OA_KAFKA_HOST_PORT` |
| MinIO API / Console | 39000 / 39001 | 应用：`OA_MINIO_ENDPOINT`、`OA_MINIO_ROOT_USER`、`OA_MINIO_ROOT_PASSWORD`、`OA_MINIO_BUCKET`；Compose 映射：`OA_MINIO_API_PORT`、`OA_MINIO_CONSOLE_PORT` |
| oa-app | 8400 | `oa-app/src/main/resources/application.yml` |
| oa-notify-service | 8401 | `oa-notify-service/src/main/resources/application.yml` |
| oa-file-service | 8402 | `oa-file-service/src/main/resources/application.yml` |
| oa-job-service | 8403 | `oa-job-service/src/main/resources/application.yml` |
| Casdoor（外部） | 8000 | 后端 `OA_JWT_*`；前端 `VITE_CASDOOR_*` |
| workflow-platform（外部） | 8300 | `OA_WORKFLOW_MODE`、`OA_WORKFLOW_URL`、`OA_WORKFLOW_KAFKA` |

## 8. 清单维护依据

后续增删组件时，应同步检查并更新以下来源：

1. `pom.xml`、`oa-dependencies/pom.xml` 和各模块 `pom.xml`。
2. `oa-console/package.json`、`oa-mobile/package.json`。
3. `deploy/docker-compose.yml` 与 `deploy/.env.example`。
4. 各部署单元的 `src/main/resources/application.yml`。
5. 各模块 `src/main/resources/db/migration/` 下的 Flyway 脚本。
6. 实际生产代码中的客户端、生产者、监听器和配置类；仅有依赖声明不等于组件已经投入使用。
