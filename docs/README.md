# 文档索引

中文为默认语言。本目录是工程文档入口；进度文件 `CODEX_PROGRESS.md` 只记录任务状态，不能代替架构或连接手册。

## 来源与状态

| 类型 | 含义 |
|---|---|
| 设计目标 | 已批准方案里打算做成什么样 |
| 仓库声明 | 当前代码、Compose、`application.yml`、`.env.example` 写了什么 |
| 环境实测 | 某次在指定机器上观察到的进程/端口/版本 |

三者冲突时并列记录来源，不把目标状态写成已部署事实。锁文件版本不等于服务端运行版本。

## 权威入口

| 职责 | 文档 |
|---|---|
| 架构 | [ARCHITECTURE.md](ARCHITECTURE.md) |
| 技术选型与历史决策 | [ADR.md](ADR.md) |
| 数据库 / 中间件 / **连接方式（公开）** | [DATABASES_AND_COMPONENTS.md](DATABASES_AND_COMPONENTS.md) |
| 运维启停与排障 | [RUNBOOK.md](RUNBOOK.md) |
| 接口契约 | [API.md](API.md)（由 `deploy/scripts/gen-api-doc.py` 生成，勿手写） |
| 权限总指南 | [AUTHORIZATION.md](AUTHORIZATION.md) · [ABAC.md](ABAC.md) |
| 实施进度（阶段冒烟） | [plans/oa-platform-0819-1721/IMPLEMENTATION_PROGRESS.md](plans/oa-platform-0819-1721/IMPLEMENTATION_PROGRESS.md) |

真实账号密码、client secret、加密密钥不在本仓库。本机私密手册路径见
[DATABASES_AND_COMPONENTS.md §7](DATABASES_AND_COMPONENTS.md)。

`docs/plans/` 与 `docs/delivery/` 中的方案/交付报告按文件内状态理解（拟议 / 已接受 / 已实现 / 已取代），不是当前运行配置的权威来源。
