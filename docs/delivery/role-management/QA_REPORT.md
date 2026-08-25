# QA Report

## Environment Profile

- Target: 本地 `oa-app` :18410 + Vite :5473
- Version or commit: 当前工作树
- Services and dependencies: PostgreSQL 16、Redis 7、Flyway、DEV 身份 `seed-user-1`
- Test data: 临时 `CODEX_ROLE_QA_*` / `CODEX_STATUS_QA_*` 角色；通过产品软删除流程清理，默认列表不可见
- Known environment limitations: 未重复执行 Casdoor JWT 登录；不影响本轮角色业务链路和 handler 权限门禁验证

## Cases

| ID | AC/Risk | Setup and steps | Expected | Actual/evidence | Verdict |
| --- | --- | --- | --- | --- | --- |
| QA-01 | AC-01 | V16 在现有本地 PostgreSQL 启动 | 迁移成功且可重复校验 | 32 migrations validated，V16 首次成功执行，二次启动 up-to-date | pass |
| QA-02 | AC-01/05/06 | API 创建带直接权限和继承的角色并查询详情 | 字段、直接权限、继承和有效权限正确 | create/detail 断言通过 | pass |
| QA-03 | AC-05 | 以 version=0 替换权限矩阵 | 版本递增、配置立即生效 | API code=0；详情与列表聚合返回正确 | pass |
| QA-04 | AC-06 | A 继承 B，B 再尝试继承 A | 拒绝环路且事务回滚 | HTTP 409，审计 `RoleAdminController#inheritance:FAILED` | pass |
| QA-05 | AC-02/07 | 停用角色后尝试新增主体授权 | 停用成功，授权拒绝 | role status code=0；grant HTTP 409 | pass |
| QA-06 | AC-07 | 父角色继承子角色；停用/启用子角色 | 停用后权限退出父角色，启用后恢复 | `STATUS_CLOSURE_QA_PASS` | pass |
| QA-07 | AC-04 | 删除无授权自定义角色并按 DELETED 查询 | 软删除成功，默认列表不显示 | delete code=0；DELETED 筛选可查；默认列表无 DELETED | pass |
| QA-08 | AC-09 | 查询统一审计表 | 成功/失败写操作均有记录 | create/delete/enabled/permissions SUCCESS，inheritance FAILED | pass |
| QA-09 | AC-08 | 浏览器打开 `/iam` → 角色管理 | 表格、统计、状态和操作正确渲染 | 四个内置角色与统计可见，内置停用/删除禁用 | pass |
| QA-10 | AC-08 | 打开新增表单和超级管理员配置抽屉 | 字段完整，危险矩阵受保护 | 新增四字段可见；权限/继承 tabs 可见；超级管理员保存按钮禁用 | pass |
| QA-11 | AC-08 | 390×844 视口检查 | 核心操作仍可见 | 搜索、新增按钮、表格均 visible | pass |
| QA-12 | AC-09/10 | 全量自动回归与契约门禁 | CI 等价命令通过 | backend reactor、107 frontend tests、build、size、contract 均通过 | pass |

## Defects And Retests

- 首轮 API QA 因预期 409 与 `curl -f` 冲突而中断；这是测试脚本问题，不是产品缺陷。保留已创建数据继续完成断言并软删除。
- 首轮前端全测发现 query key 缺少 `permVersion`；修复后 107/107 通过。
- 审查发现停用继承节点仍可能贡献权限；修复闭包重建后真实 PostgreSQL 停用/启用复测通过。

## Automated Regression

- Backend: `mvn test` — pass，16/16 modules。
- Frontend: 107/107 tests — pass。
- Contracts: permission/OpenAPI checks — pass。
- Build: TypeScript/Vite — pass。
- Bundle: 288.2 KB / 300 KB — pass。

## Blocked External Checks

- 无阻塞项。远程 GitHub Actions 尚未实际触发；本地执行了相同核心命令。

## Verdict

pass
