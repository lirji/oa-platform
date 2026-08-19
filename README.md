# oa-platform

企业级 OA 协同办公平台（万人级 / 10,000 员工）。**模块化单体 + 3 个独立服务**。

- 计划：`../docs/plans/oa-platform-0819-1721/FINAL_PLAN.md`
- 决策：`../docs/plans/oa-platform-0819-1721/DECISION_RECORD.md`（ADR-0001~0011）
- 进度：`../docs/plans/oa-platform-0819-1721/IMPLEMENTATION_PROGRESS.md`（**权威进度文件**）

## 它解决什么

不是"又一个 CRUD 后台"。核心难点只有两个：

1. **组织与权限的建模深度** —— 任意层级组织树、一人多岗、独立汇报线、部门权限继承、
   临时授权三形态（定时角色 / JIT 提权 / 委托代理），以及**页面 / 接口 / 数据**三类权限的一致落地。
2. **万人级的性能** —— 判权在每个请求的热路径上，组织范围计算在每条 SQL 上。
   本项目的答案是：**判权全程本地内存判定，零 DB 零远程**（组织树常驻 + 权限位图快照三级缓存），
   数据权限用 `org_path` 前缀匹配而非 `IN (5000 个 id)`。

认证复用 [Casdoor](../auth-platform)（:8000），审批编排复用 [workflow-platform](../workflow-platform)（:8300）。

## ⚠️ 构建必须用 system maven

中台制品（`com.lrj.workflow` / `com.lrj.authz`）装在 **`/Users/liruijun/personal/repository`**，
不在 `~/.m2`。**本项目刻意不提供 `mvnw`** —— 用它会走 `~/.m2` 从而解析不到中台制品。

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin:$PATH"
mvn clean install -DskipTests
```

## 快速开始

```bash
cp deploy/.env.example deploy/.env
bash deploy/scripts/phase0-smoke.sh      # 起基建 + 两道硬门禁 + 构建 + 启动自检
```

## 端口

| 组件 | 端口 |
|---|---|
| oa-app（主应用） | 8400 |
| oa-notify-service（WebSocket 推送） | 8401 |
| oa-file-service | 8402 |
| oa-job-service（跑批） | 8403 |
| oa-console（PC，React+antd） | 5473 dev / 8404 prod |
| oa-mobile（H5） | 5474 dev / 8405 prod |
| PostgreSQL / Redis / Kafka / MinIO | 35432 / 36379 / 39092 / 39000·39001 |

避让说明：9092 属 langchain4j-platform、29092/25432 属 workflow-platform、15432 属 auth-platform，**一律不动**。

## 模块

```
oa-common      Result/异常/TenantContext/号段生成器
oa-protocol    对外 DTO + 事件契约（与独立服务、前端共享语义）
oa-security    JWT/UserContext/@RequiresPerm/@DataScope/@Sensitive + PermissionChecker 端口（不含实现）
oa-org         组织域 + OrgTreeCache + OrgQueryApi
oa-iam         权限域 + PermissionEngine 三级缓存（PermissionChecker 的实现方）
oa-flow        表单引擎 + 单据 + workflow-platform 集成 + todo_item CQRS 读模型
oa-attendance / oa-doc / oa-admin-biz / oa-report
oa-app                主应用，装配以上全部
oa-notify-service     按【连接数】扩容，故独立
oa-file-service       大流量 IO，故独立
oa-job-service        跑批与在线争 CPU，故独立
```

## 三条不可动摇的纪律

1. **接口权限是唯一安全边界**。前端菜单/按钮裁剪只是体验。
   每个 handler 必须有 `@RequiresPerm` 或 `@PublicApi(reason=...)` —— `ControllerPermissionCoverageTest` 会让构建失败。
2. **跨模块只能走 `..api..`**。直接注入对方 Mapper/Entity 会被 `ArchitectureRulesTest` 拦下。
3. **业务单据表必须冗余 `org_id` + `org_path`**，且 `org_path` 建 `text_pattern_ops` 索引
   （PG 非 C locale 下普通索引不走前缀 LIKE）。
