# P1-04 OA 来源端实现与验证

本来源端 pass 已验证，可作为完整逻辑单元提交；P1-04 整体仍 IN_PROGRESS，双库端到端与 P1-07 尚未完成。

## 实现范围

沿用 auth 仓 CONTRACTS_P1_DIRECTORY.md。共享协议依赖 auth fd03979b1a996d2626215be55f60734508c6d7d9，CI 固定该提交。
复用 PostgreSQL、Spring Security 与 MyBatis，不新增常驻服务、消息服务或缓存。

- V8 新增来源登记、聚合版本、连续分区序号、不可变 Outbox 与确认水位，表及字段注释齐全。隔离库已应用，不改历史迁移。
- 员工七类写入口、组织四类写入口在业务修改前取得目录门闩，业务/版本/序号/事件同事务。捕获不受 HTTP 开关控制；离职检查影响行数，只表示等待跨平台确认。
- 初始化最多 10000 条、16 MiB 正文、60 秒事务，BEGIN/END 和业务事件原子封存；重试不重置游标，超限整批回滚，不按缺失删除。
- COPY/truncate 与初始化共用数据库事务排他门闩，普通写入取得共享门闩与企业行锁；已接管来源拒绝旁路。JDBC 保护在 COPY 的同一连接，SQL 位于持久化辅助类。
- 服务出口默认关闭，固定企业/来源，服务端只存凭据 SHA-256，恒定长度比较；TLS 或显式许可的真实回环连接。专用安全链、Handler 守卫与用例层共同检查 ServiceIdentity，不接受员工 JWT/isAdmin/请求租户头。
- 事件页和确认正文有界；同摘要/旧确认不回退水位，未知序号/异摘要拒绝。初始化 CLI 要求 0600 配置，只操作隔离回环 PG，不自动迁移。
- MyBatis 拦截器保持启用。系统读取声明既有 DataScopeBypass 审计元数据，固定企业 SQL 读取源事实表，不借个人通讯录视图生成残缺快照。

## 实现中修复的问题

两仓 Maven 本地仓库不同，已把 protocol 安装到 OA 使用的目录。完整宿主拦截器发现 JSqlParser 不支持多列冲突目标，版本分配改成来源锁内 insert-if-absent / update / select，保留同事务与溢出拒绝。
初版 InterceptorIgnore 被项目检查拒绝，已移除；未放宽原检查。Controller 正文解码下沉为协议 DTO，保留禁止 Controller 依赖 Mapper 的检查。

## 独立验证 pass

由 implementation-validation pass 核对实际证据，未使用独立子 Agent。

- 全仓 `mvn -B verify -Pdirectory-it`：192 项单测（含 5 HTTP），191 PASS、1 既有跳过；13 PG IT 全 PASS。
- 随后仅去除无用 import、CLI 注入输出流；5 HTTP + 13 PG 与依赖编译窄重跑全部 PASS。
- 真实 PG 使用专用 auth_gov_p1_test_d69530118e13，执行原 V1 和组织迁移，装配宿主数据权限、乐观锁、防全表、分页拦截器。未连接共享 OA 库。
- PG 覆盖业务与事件回滚、离职 update 返回零、并发来源锁/回滚无空洞、单调版本、快照摘要/幂等、超限回滚、跨企业拒绝、组织环、COPY/truncate 保护、确认幂等/不回退、CLI 权限与重试。
- HTTP 覆盖默认关闭、DEV 普通链不能绕过、错误/重复凭据、员工 Token 拒绝、TLS/回环、路径/方法允许列表、固定企业、重复/未知/尾随/错类型/超长 JSON 拒绝。
- Code Hygiene：IMPLEMENTATION_COMPLETE_WITH_LIMITATIONS。项目无规范格式化工具；事务、异常和协议上限人工检查通过。没有远程调用进入业务事务。
- API/DataAccess Golden 仅新增本片服务接口及系统读取，接口标为 service:oa-directory，没有伪装为 PublicApi。
- 打包后的 OA JAR 经 PropertiesLauncher 执行初始化 CLI，两次运行退出码均为 0，重跑没有重复初始化；日志 oa-packaged-cli.log。
- PC Console 的 `gen:perm --check`、`gen:api:check` 均 PASS，内部出口未漂移既有页面生成契约。

日志在 auth 主仓 `.local/governance/p1-04/`：oa-full-final-2.log、oa-final-focused.log、oa-source-hygiene-final.json；前序失败日志保留。源码指纹见 P1-04-source-evidence-index.json。

## 待完成

远程 CI 待推送运行。真实跨仓拉取/双库故障恢复/Casdoor 员工上下文与离职链仍待 P1-04 后续 pass 验证。本批次不标整体 DONE，不进行正式接管或生产部署。既有 OA 用户文档和 tmp/ 不纳入提交。
