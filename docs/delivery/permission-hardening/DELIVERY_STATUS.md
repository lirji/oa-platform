# Delivery Status

## Goal

完成权限安全收口，并在全量验证后执行 Docker 完整冷构建与本地部署。

## State

- Phase: complete
- Status: delivered
- Last updated: 2026-08-25

## Completed

- AC-01：JIT 改为 elevation-only，只激活角色中标记为高危的权限位，不贡献 RBAC、ABAC 或数据范围。
- AC-02：委托查询与办理统一消费服务端规则，伪造 `onBehalfOf` fail-closed。
- AC-03：员工详情和全部写操作补齐目标对象数据范围断言。
- AC-04：公文、知识库、资产、会议室、访客、流程、报表和文件等外部请求数据访问迁移到受管 Mapper 或显式对象权限。
- AC-05：ABAC 返回实际通过的来源角色，后续只合并这些来源的数据范围。
- AC-06～08：PC 主体授权支持可搜索用户/组织、CUSTOM 组织树、下级开关、时效、理由和角色默认/最大范围；服务端校验主体、租户、上限、自授和高权角色。
- AC-09：重复活跃授权使用事务 advisory lock 幂等；撤权和任职范围收缩推进全局 epoch。
- AC-10：新增 JIT PENDING/批准/拒绝四眼审批、审批台和审计关联；申请人不可自批。
- AC-11：权限、ABAC、API、架构、ADR、运行手册、CI 与交付文档已同步。

## Verification Log

| Command or check | Result | Notes |
| --- | --- | --- |
| `mvn -pl oa-iam -am test` | pass | IAM 55 tests；JIT、ABAC 来源范围、授权治理、缓存收权 |
| `mvn -pl oa-notify-service,oa-file-service,oa-app -am test` | pass | 14-module reactor；应用 Golden/架构、公告 ACL、文件对象权限全部通过 |
| `npm test -- --run` | pass | 12 个测试文件、109 个用例全部通过 |
| `npm run gen:perm -- --check` | pass | 权限常量与后端目录一致 |
| `npm run gen:api:check` | pass | 运行态 OpenAPI 快照及 TypeScript 类型一致；106 paths |
| `npm run build && npm run size` | pass | TypeScript/Vite 构建通过；首屏 gzip 288.5 KB / 300 KB |
| `docker compose ... config -q` | pass | apps profile 配置有效 |
| Docker cold build/deploy | pass | 4 个后端、PC、移动端均以 `--pull --no-cache` 构建；6 个应用容器重建且 healthy |
| Flyway/runtime smoke | pass | V17/V83 成功；角色管理、提权列表、6 个服务 ping、401 与 OpenAPI 冒烟通过 |

## Decisions And Deviations

- 当前产品继续按单租户运行，但所有本轮新增/修改的授权查询都显式带 `tenant_id`；真实多租户启用仍不在本轮范围。
- `role.default_scope` 同时作为默认范围和最大可分配上限，避免新增 schema/迁移的双配置漂移。
- JIT 四眼审批使用 IAM 本地审批表，不反向依赖 flow 模块，避免模块循环；保留审批关联字段供未来外部 BPM 编排。
- 对象权限由 `@ObjectScope` 冻结入口立场，OWNER/ACL/参与者的实际谓词仍由服务或 SQL 执行并由架构测试守护。

## Blockers And Residual Risks

- 暂无阻塞。
- 远程 GitHub Actions 尚未触发；本地执行 CI 等价门禁。
- `default_scope` 的默认值/最大值双重语义和真实多租户启用仍属于后续产品演进项，不影响本次单租户交付。

## Next Action

交付完成。后续如启用真实多租户或需要“默认 SELF、最大 ORG”这种双层范围模型，应另立需求处理。
