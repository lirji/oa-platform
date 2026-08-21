# USER_GROUP / ABAC Code Review

## Verdict

Pass。未发现阻塞交付的正确性、安全或兼容性问题；生产启用 ABAC 仍需执行 Runbook 灰度步骤。

## Scope Reviewed

- V14 schema、租户边界、成员生命周期与授权写侧。
- USER_GROUP 快照构建、时间边界、角色继承、数据范围、缓存失效和来源解释。
- ABAC 表达式安全、条件组合语义、快照/L2 codec、AOP 和观测指标。
- Controller 授权立场、PC 管理交互、OpenAPI/golden、部署配置、冒烟与文档。

## Findings Resolved

| Severity | Finding | Resolution |
| --- | --- | --- |
| High | `permission_condition` 初版缺租户列和 CRUD tenant predicate | V14 增加 `tenant_id`，mapper/service/role target 全链路约束 |
| High | 未知权限或损坏快照缺分支时 ABAC 可能按兼容路径放行 | 改为 fail-closed，并新增单测 |
| High | 恶意 SpEL 可成为代码执行入口 | SimpleEvaluationContext + type/Bean/constructor/method/assignment deny-list + 对抗测试 |
| Medium | 编译表达式缓存和单目标条件数无界 | Caffeine 10,000 上限；每个 role-permission 最多 32 条活跃条件 |
| Medium | 用户组列表的 nullable bind 可能触发 PostgreSQL 类型推断问题 | 改为 MyBatis 动态条件 SQL |
| Medium | 员工查询依赖错误会被统一伪装成“员工不存在” | 只转换 `EMPLOYEE_NOT_FOUND`，其它依赖错误原样传播 |
| Medium | golden 工具不识别 class-level `@RequiresPerm` | 提取器继承类级授权/公开立场，与 AOP 一致 |
| Low | 成员 ID 在 trim 前去重会重复 upsert | trim 后再 LinkedHashSet 去重，并扩展测试 |

## Security Review

- 所有新增 handler 要求 `oa:iam:admin`；组授权仍分别要求现有 grant/view/revoke 权限。
- ABAC 不暴露 Spring Bean、Class、request/response，不记录业务参数值；错误按拒绝处理。
- 条件和用户组管理按 `TenantContext` 限制，角色 target 也验证当前租户。
- 组禁用、成员增删、条件生效变化均推进全局 epoch；ABAC 开关状态写入快照，避免复用模式不一致的 L2 数据。
- 物理删除不用于用户组；授权/成员/条件测试数据均通过业务生命周期回收。

## Non-blocking Notes

- AOP 在 ABAC 分支中会再做一次 L1 位图查询；它是内存 O(1)，当前不构成性能风险，可在后续性能专项中合并。
- ABAC 是 role-permission 级策略，会作用于使用该 permission 的所有 handler；配置界面和 Runbook 已明确该边界。
- 首版不支持动态/嵌套组和业务对象属性提供器，这是已批准的范围限制。
