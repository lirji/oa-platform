# OA 本地 Docker 重新部署记录

- 日期：2026-10-02（America/Los_Angeles）。结果：**PASS**。
- 目标：Docker Desktop `desktop-linux`，Compose 项目 `oa-platform`。
- 产品源码：`a9994e01aecc4ef0d664970bbcc3e45d3c22ed1e`，部署时与 `origin/main` 一致。
- 六个应用镜像均重新构建，标签 `rev-a9994e0`；运行镜像 ID 见下表。数据库、Redis、Kafka、MinIO 使用原容器和原卷。

| 服务 | 地址 | 镜像 ID |
|---|---|---|
| oa-app | http://localhost:18400 | `sha256:6c09740a3f916c851c3b7ccf45296200c027e885ee99de21bd4763b89549ee2a` |
| oa-notify | http://localhost:8401 | `sha256:dbf9d20e2a60863442e60a9be305ae5ee803a544c8edffa1084703d69994da3c` |
| oa-file | http://localhost:8402 | `sha256:d2936f894770bf01763bcbe5b4a53246e487b1a8a372b4e4cfd51a0ba999cdef` |
| oa-job | http://localhost:8403 | `sha256:a7eeab4a809593d93a3d9a904c1e2affb67544ad8a9208a7561eaafde8da2b9e` |
| oa-console | http://localhost:8404 | `sha256:4459a179c3f8b546b1f33da7e3c444dbb82b53014a378932cf2742aaa5be27c3` |
| oa-mobile | http://localhost:8405 | `sha256:b2fe2915b158c2997e1406d7c0c02a37d3aaf10669dda71a40e81075115644c0` |

## 验收

- 精确源码 CI [36669495481](https://github.com/lirji/oa-platform/actions/runs/36669495481)：Backend / JDK 21、PC Console / Node 20、Mobile H5 / Node 20 全部成功。
- 当前本地构建：Maven clean package；PC、移动端镜像内生产构建、契约检查和包体预算通过。PC 生成权限契约检查、14 文件 / 125 测试通过；移动端镜像构建执行其测试。
- 四个后端镜像内 JAR SHA256 与本次宿主新构建制品一致。PC 56 文件、移动端 6 文件的 HTTP 返回与容器静态文件逐字节一致。
- 10 容器均 healthy，原端口不变；均设置 `unless-stopped`。12 项健康、ping、匿名认证拒绝 HTTP 检查通过。
- 真实浏览器分别完成 PC 和移动端 OIDC 授权码 + PKCE 登录；PC 权限中心身份表、移动待办页面实际加载；三个受保护只读 API 返回 200 / code=0；页面脚本错误为 0。未办理待办、创建授权或重新灌种子。

## 数据与恢复

- 部署前已生成 PostgreSQL 完整自定义格式备份、MinIO 文件副本，原镜像保留 `local-rollback/<容器名>:before-20261002` 标签。
- 原 Flyway 历史全部保留，按项目已有 out-of-order 规则新增 V8（目录 Outbox）、V25（中央审批映射）、V26（决策 Outbox），均成功；没有 repair 或改写已执行历史。
- 114 张原有业务表的全部数据行规范排序 SHA256 与部署前备份一致，新增六张迁移表；Flyway 历史另行核对。原四个中间件镜像 ID 和卷挂载保持一致。
- 原 JWT、Casdoor、数据库凭据、加密密钥和 LOCAL 工作流配置保留。未自动切换中央 IAM 接入配置。
- 回退应用前须检查新表与旧代码兼容性；数据恢复须在独立验证后执行，镜像回退不等同于数据库恢复。

## 运维与证据

本次私密证据及备份在 `.local/docker-redeploy-20261002/`（忽略目录），包含原配置、原文件、运行版本、制品摘要、CI、迁移、数据摘要、HTTP、浏览器回执和截图。首次数据比较脚本的空 COPY 块解析问题、首次移动端假设自动登录及第二次错误文本选择器的失败均保留；修正验证脚本后独立验收通过，未修改产品代码。

未来更新使用 `deploy/build-images.sh` 重建和中央 `auth-platform/deploy/platform-compose.sh oa --profile apps` 部署。现有应用重启不会重新编译。`docker update --restart unless-stopped` 是本次容器运行配置；以后若重建容器需再次核对该策略。

Git 仅提交本次新增报告和进度检查点；任务开始前已有的进度/设计文档改动保留，不混入交付。未新建、移除 worktree，原配置、备份、回退镜像均需保留。
