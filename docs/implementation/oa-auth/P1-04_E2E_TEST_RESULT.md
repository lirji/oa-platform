# P1-04 真实双库与身份源验收

本地 Gate PASS；本批次 bc0734b 的远程 CI 36398882953 SUCCESS（后端、PC、移动端全部通过）。P1-04 总体验收由 auth 协调仓汇总，不在单仓文件中提前标完整交付。

## 真实链路

真实 OA EmployeeService/OrgUnitService → 专用来源 PostgreSQL → 不可变 Outbox → 实际 Tomcat/MVC/服务安全链/目录 Controller → auth DirectoryPullImporter → 独立治理 PostgreSQL → 当前成员读取。
第二轮接入隔离固定 Casdoor v4.11.0 签发的真实 Access Token，校验签名/用途/introspection，再验证来源离职影响。

测试宿主仅装配所需组件，故障注入只在 src/test 中；没有启动共享 OA 或连接其他业务组件。
JAR 内容检查确认 auth-platform-governance 的 test 依赖、测试宿主与故障过滤器均未进入 OA 生产制品。

## 可观察结果

- 源端 BEGIN/组织/员工/END 的四事件快照按每次两条导入。
- 首次确认已在源数据库提交，但测试过滤器向消费者返回 503；导入失败时两库水位均为 2，员工尚未导入，既有其他员工仍保留。
- 重跑补确认后继续导入员工与 END，目标水位达到 4，精确 issuer/sub 可取得新员工当前上下文。
- 真实 OA leave 产生序号 5；未确认时源水位 5/4，不能宣称撤权完成。导入后确认达到 5，成员从有效列表消失、上下文解析拒绝；再次导入处理量为 0。
- 真实 Casdoor 场景中，离职前 Access Token 可读该成员；离职后相同 Token 仍通过签名/用途校验，但不能读到退出的成员。ID Token 被拒绝。
- 不完整快照、完整快照及后续重跑都未删除与来源快照无关的既有成员。

## 验证证据

`mvn -B verify -Pdirectory-it` 全仓通过；192 单测中 191 PASS、1 既有跳过；15 PG 集成测试全部 PASS、无跳过（含两种真实 HTTP 双库场景，其中一个使用真实 IdP）。
日志在 auth 主仓 `.local/governance/p1-04/dual-db-idp-full.log`；专属 IdP 夹具 `.local/governance/p1-04-idp/run-01`，未绑定 P1-02 原外部负例账号。
Hygiene 为 IMPLEMENTATION_COMPLETE_WITH_LIMITATIONS，限制为仓库无统一 formatter。源码摘要见 P1-04-e2e-evidence-index.json。

auth 依赖固定 b11d080ae38024c482ab49e4dd3a9dce2993835e，其 CI 36398245134 SUCCESS。OA 上一来源批次 626ecc8 的 CI 36397627546 SUCCESS。
当前 OA CI 同时创建源库和独立治理库/角色；基础双库 HTTP 测试必跑。真实 IdP 专属测试只有显式私密环境变量时运行，CI 不把跳过该测试当作真实 IdP 证据；本地上述记录已证明它通过。

实现期间仅修正测试装配：改用治理公开用例而非 package-private Mapper、测试宿主隔离普通 application.yml。没有为测试放宽生产访问边界，也没有回退/重写已执行 V8。

同一执行者在实现后执行验证 pass，未声明独立子代理评审。该证据不授权正式切流、共享 Casdoor 升级或生产部署。
