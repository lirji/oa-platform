# Permission Hardening Delivery Report

## Outcome

权限体系加固已完成，AC-01～AC-12 全部通过。系统现在具备角色新增/复制/编辑/启停/删除、权限矩阵与
继承、主体授权和撤权、CUSTOM 组织范围、ABAC 二次收窄、JIT 四眼审批、委托校验、对象级权限、可靠
缓存收权及审计关联。相关文档、OpenAPI、CI 门禁、数据库迁移和控制台入口已同步。

## Delivered Capabilities

- 角色生命周期：创建、详情、更新、复制、启停、删除、权限替换和继承关系维护。
- 授权治理：可搜索用户/组织主体、时效、理由、CUSTOM 组织树、范围上限、自授/越级高权保护和幂等。
- ABAC/数据范围：只合并本次通过 ABAC 的来源角色范围，避免未通过角色的宽范围串入。
- JIT：只激活已持有角色中的高危权限位；申请保持 PENDING，审批人不可与申请人相同，批准后才生效。
- 业务数据面：员工、流程、公文、知识、资产、会议室、访客、公告、文件和报表补齐范围或对象 ACL。
- 可靠性：撤权、组织变化和角色变更推进权限 epoch；旧快照协议 fail-safe 重建。

## Regional Manager Configuration

“华东大区经理只能查看华东数据”应以数据范围为主：角色 `defaultScope=CUSTOM`，授权时选择“华东大区”
组织节点并开启下级组织。只有还需要按金额、时间、请求参数等条件继续收窄时，才在具体权限点上增加
ABAC。ABAC 不能代替 CUSTOM 数据范围。

## Verification Evidence

| Gate | Result |
| --- | --- |
| Backend | `mvn clean package` pass；14 模块聚合测试 pass；IAM 55 tests |
| Frontend | 12 files / 109 tests pass；TypeScript/Vite build pass |
| Generated contracts | permission codes 和 OpenAPI TypeScript check pass；运行态 106 paths |
| Bundle budget | 首屏 gzip 288.5 KB / 300 KB |
| Compose | apps profile `config -q` pass |
| Cold images | 4 backend + PC + mobile 全部 `--pull --no-cache` pass |
| Deployment | 10 个容器 healthy；6 个应用容器使用最终镜像 |
| Database | Flyway V17/V83 success；提权审批表和审批权限存在 |
| Runtime | 6 个服务 ping、角色管理、JIT 管理/个人列表 pass；未认证访问 401 |
| Logs | 最终正确冒烟后的应用日志窗口无 ERROR/Exception/FATAL |

Docker Hub 在首次拉取时出现瞬时 TLS timeout/EOF；重试后所有最终镜像均完成无缓存构建。运行态 QA 还发现
并修复了提权列表的 PostgreSQL 可空参数类型问题，oa-app 已重新冷构建和替换，复测为 200。

## Deployment State

- 主应用：`http://127.0.0.1:18400`
- PC 控制台：`http://127.0.0.1:8404`
- 移动端：`http://127.0.0.1:8405`
- notify/file/job：`8401` / `8402` / `8403`
- 环境：`OA_SECURITY_MODE=DEV`，`OA_IAM_ABAC_ENABLED=true`

## Residual Decisions

- 当前仍是单租户产品；真实多租户启用、RLS 或跨租户测试矩阵不在本次范围。
- `role.default_scope` 当前同时是默认值和最大可授范围，不能表达“默认 SELF、最大 ORG”的双配置。
- 远程 GitHub Actions 未触发；本地已执行对应测试、构建、Golden、生成物和 compose 门禁。
