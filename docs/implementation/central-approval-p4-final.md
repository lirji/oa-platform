# 中央权限申请 P4 最终验收

OA继续拥有真实流程与待办；auth拥有不可变申请、Grant及生效回执。复用oaGenericApproval、既有Outbox和REMOTE Flowable，不新增BPM，不把原OA IAM JIT当成中央普通申请授权。

本地G4已通过：真实Casdoor PARTNER受邀成员提交申请，中央签名幂等启动，指定审批人查看固定依据并办理真实任务，OA凭实际节点日志和引擎终态发布签名事件；丢首个回调ACK后稳定event/body换nonce重试，auth只创建一个Grant，图失败拒绝，恢复实际生效；OA停止后正常授权独立工作，到期和申请回收只影响对应来源。

证据：auth仓库docs/implementation/oa-auth/phase-4/P4-07_TEST_RESULT.md与evidence/e2e.json；主申请be52c9e3-a005-465b-a5a0-34ce6c3514c8、OA实例22、生效operation 9aeb5067-89b8-4d8c-8234-8b8110a3650a、回收operation 61976b9f-13ff-4545-a531-5c8baa799145。

真实验收发现并修复：异步任务早于实例回绑导致待办缺业务引用，回绑后补齐元数据且不复活DONE；原注解SQL的XML实体造成完成写入失败，改为真实SQL比较并用PG回归。回调扫描先按绑定分区过滤再LIMIT。新增GET /api/v1/flow/central-access/tasks/{taskId}只允许实际指定审批人读取固定Start快照；外部申请人403。仅同步本片3个API及4个schema，未修改其他历史OpenAPI漂移。

验证：mvn verify -Pdirectory-it全reactor exit0；195单测（1既有条件跳过）、31集成项（1额外目录Casdoor夹具测试条件跳过）；P4 CentralApprovalPostgresIT 5项无跳过。权限/API golden、架构检查、Code hygiene、OpenAPI生成一致性、TypeScript与控制台build均通过。统一Java formatter未配置为工具限制，不伪称已运行。P4真实身份验证由上述独立E2E覆盖，未将历史条件跳过算作通过。

迁移V25/V26已执行，禁止改历史；独立HMAC方向密钥、TLS/受控loopback、精确中央分区与成员代际桥接均为显式配置。默认不开中央入口。生产TLS/ACL、保留期限、常驻调度和P5页面不在本次交付承诺内。

Git/远程CI仍以auth P4_DELIVERY_RESULT/CI_RESULT及后续OA交付记录为准。用户已有CODEX_PROGRESS、identity-authz-governance进度/部署文件、tmp和.local保留，不纳入任务提交。没有生产部署或清库。

## 远程 CI 扫描修复

首次远程 CI 36578859559 的前端检查通过，后端 JDBC 旁路门禁误把 `.ci-deps/auth-platform` 的源码计入 OA 基线。没有增加旁路豁免：扫描改为仓库直属 Maven 模块的 `src/main/java`，同样用于禁止关闭数据权限拦截器的检查。新增临时目录回归，确认 OA 生产代码仍被扫描，嵌套依赖仓库、测试和构建产物不被混入；定向 `JdbcBypassArchitectureTest` 两项通过。后续远程 CI 的最终结果由 auth `CI_RESULT.md` 记录。
