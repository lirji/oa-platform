# ABAC 授权与管理指南

本文说明 OA 当前受限 ABAC（Attribute-Based Access Control）的授权边界、表达式上下文、
策略组合规则、管理端操作以及启停方式。系统仍以 RBAC 为基础：用户先通过授权主体获得角色和权限点，
ABAC 再决定本次带有该权限点的接口调用是否放行。

RBAC、接口权限、数据权限和字段权限之间的完整关系，以及 RBAC/数据范围的编辑方式见
[权限体系与全局保障指南](AUTHORIZATION.md)。

## 1. 启用、确认与回退

ABAC 是环境级总开关，代码默认保持关闭。目标环境在 `deploy/.env` 中设置：

```dotenv
OA_IAM_ABAC_ENABLED=true
```

然后重建主应用容器使配置生效：

```bash
docker compose --env-file deploy/.env -p oa-platform \
  -f deploy/docker-compose.yml --profile apps up -d --force-recreate oa-app
```

确认容器变量、健康状态和启动日志：

```bash
docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' oa-app \
  | grep OA_IAM_ABAC_ENABLED
docker inspect --format '{{.State.Health.Status}}' oa-app
docker logs oa-app 2>&1 | grep 'ABAC 条件引擎'
```

应看到 `OA_IAM_ABAC_ENABLED=true`、`healthy` 和 `ABAC 条件引擎：启用`。开关启用但没有配置
ABAC 条件时，现有 RBAC 授权仍按无条件来源放行。

需要紧急回退时，将环境变量设为 `false` 并重启 `oa-app`。这只关闭 ABAC 求值，不需要回滚 V14
数据库迁移，也不会删除已配置的条件；重新开启后条件会再次生效。

## 2. 授权维度

ABAC 策略的挂载维度是：

```text
租户 × 角色 × 权限点 × 条件表达式
```

- 每条条件归属一个租户、一个角色和一个权限点。
- 角色必须直接或通过角色继承拥有目标权限点。
- 条件会作用于所有使用该权限点进行 `@RequiresPerm` 判权的后端方法。
- 角色可以授予 `USER`、`ORG_UNIT`、`POSITION` 或 `USER_GROUP`；无论用户通过哪一种主体获得该角色，
  都会执行该角色对应的 ABAC 条件。
- `USER_GROUP` 是显式成员组，成员有效期和组状态先决定用户能否获得角色，ABAC 再执行属性判断。

ABAC 当前控制的是一次接口方法调用是否允许，不是字段级脱敏，也不会直接生成 SQL 行过滤条件。
RBAC/ABAC 实际通过的权限点会进入本次授权上下文；`@DataScope(permission=...)` 只能引用其中的权限点，
再读取该权限点自身的 `ALL`、`ORG_AND_SUB`、`ORG`、`SELF`、`CUSTOM`、`NONE` 范围生成 SQL。
同模块其他权限的 `ALL` 不会放宽当前权限。平台严格协议和剩余模块迁移见
[接口权限与数据权限全局保障方案](plans/data-permission-global-guard/FINAL_PLAN.md)。

## 3. 可用属性

表达式使用受限 SpEL，只能读取当前用户上下文和当前接口方法参数。运行时不会为了 ABAC 查询数据库
或调用远程服务。

### 3.1 当前用户 `#user`

| 属性 | 含义 | 类型 |
|---|---|---|
| `#user.userId` | Casdoor `sub`，全局用户标识 | String |
| `#user.username` | 用户名 | String |
| `#user.employeeId` | OA 员工 ID，未入职账号可为空 | Long / null |
| `#user.primaryOrgId` | 主岗组织 ID | Long / null |
| `#user.primaryOrgPath` | 主岗组织物化路径，如 `/1/23/456/` | String / null |
| `#user.tenantId` | 当前租户 ID | long |

### 3.2 方法参数

| 写法 | 含义 |
|---|---|
| `#p0` / `#a0` | 第一个方法参数 |
| `#p1` / `#a1` | 第二个方法参数，以此类推 |
| `#args[0]` | 通过参数数组访问第一个参数 |
| `#request` | 编译产物保留参数名时，使用真实方法参数名 |
| `#p0.amount` | 读取第一个参数对象的 `amount` 属性 |

常用表达式示例：

```spel
#user.tenantId == 1
```

```spel
#p0.amount <= 5000 and #user.primaryOrgId == 10
```

```spel
#p0.userId == #user.userId
```

```spel
#user.primaryOrgPath != null and #user.primaryOrgPath matches '^/1/23/.*'
```

可以使用比较、布尔、空值判断、基础算术和只读属性访问。单条表达式最长 512 字符。

## 4. 条件组合与安全语义

1. 同一个“角色 + 权限点”下的多条启用条件按 `AND` 计算，必须全部满足。
2. 用户通过不同角色获得同一权限点时，各角色条件分支按 `OR` 计算，任一分支满足即可。
3. 只要存在一个适用角色没有配置该权限点的启用条件，该来源视为无条件授权并直接放行。
4. 一个表达式内部可自行使用 `and`、`or`、`not` 组合。
5. 未知权限点、缺少条件分支、变量/属性不存在或求值异常均按拒绝处理（fail-closed）。
6. 同一“角色 + 权限点”最多配置 32 条未删除条件。

因为策略绑定权限点，而一个权限点可能被多个接口共同使用，编辑前必须检查这些接口的方法参数形状。
例如条件使用 `#p0.amount`，但另一个使用相同权限点的方法第一个参数没有 `amount`，该接口求值会出错并被拒绝。

为防止表达式执行任意代码，系统禁止：

- `T(...)` 类型引用和 `new` 构造器；
- `@bean`、`#root`、`#this`；
- 方法调用、赋值；
- `Class`、`ClassLoader`、`Runtime`、`ProcessBuilder`、`System` 访问；
- 集合选择、投影等可执行语法。

## 5. 在管理端编辑

管理入口：PC 管控台 `/iam` → “权限策略” → “ABAC 条件”。操作账号必须拥有
`oa:iam:admin`。

新建或修改条件：

1. 点击“新建条件”，或在已有条件行点击“编辑”。
2. 选择角色。
3. 选择权限点；角色必须直接或通过继承拥有该权限。
4. 输入受限 SpEL 条件和便于审计的说明。
5. 点击“仅校验表达式”，确认语法和安全限制通过。
6. 首次配置建议关闭“启用”后保存，审查该权限点的全部后端使用位置，再打开开关。
7. 列表中的开关可即时启停条件，也可以软删除条件。

所有影响已启用策略的创建、修改、启停和删除操作都会推进权限全局纪元，使已有权限快照失效。
下次判权会使用新策略，不需要手工清理 Redis。

## 6. 管理 API

所有接口都要求 `oa:iam:admin`：

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/iam/abac/conditions` | 查询条件，可按 `roleId`、`permissionId` 筛选 |
| POST | `/api/v1/iam/abac/conditions` | 新建条件 |
| PUT | `/api/v1/iam/abac/conditions/{id}` | 修改条件 |
| POST | `/api/v1/iam/abac/conditions/{id}/enabled?enabled=true` | 启停条件 |
| DELETE | `/api/v1/iam/abac/conditions/{id}` | 软删除条件 |
| POST | `/api/v1/iam/abac/validate` | 只校验表达式 |

真实环境启用前应先运行：

```bash
OA_PHASE9_APP_BASE=http://127.0.0.1:8400 \
  bash deploy/scripts/phase9-iam-policy-smoke.sh
```

并监控 `oa_iam_abac_evaluations_total`、`oa_iam_abac_denied_total`、
`oa_iam_abac_errors_total`。`denied` 或 `errors` 异常增长时，应先停用最近修改的条件；无法快速定位时
关闭 ABAC 环境开关并重启主应用。
