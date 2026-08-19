# oa-platform 工作约定（给 Claude Code 读）

## 构建
**必须用 system maven**，不要用 `./mvnw`（本项目刻意没有 mvnw）：
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin:$PATH"
```
中台制品 `com.lrj.workflow` / `com.lrj.authz` 在 `/Users/liruijun/personal/repository`，不在 `~/.m2`。

## 权威文档
- 计划 `../docs/plans/oa-platform-0819-1721/FINAL_PLAN.md`
- 决策 `../docs/plans/oa-platform-0819-1721/DECISION_RECORD.md`（ADR 已拍板，**不要再推翻**）
- 进度 `../docs/plans/oa-platform-0819-1721/IMPLEMENTATION_PROGRESS.md` ← **以它为准**

## 硬约束
1. 新增 REST handler **必须**带 `@RequiresPerm("oa:模块:动作")` 或 `@PublicApi(reason="...")`，否则构建失败。
2. 跨模块只能依赖对方 `..api..` 包，不许注入对方 Mapper/Entity（ArchUnit 会拦）。
3. 业务单据表必须冗余 `org_id` + `org_path`；`org_path` 索引必须是 `text_pattern_ops`。
4. 数据权限一律走 `org_path LIKE '前缀%'`，**不许**展开成 `org_id IN (大列表)`。
5. 主体标识统一用 Casdoor `sub`（UUID 字符串），**不许**用自增 Long。
6. 对 `workflow-platform` / `auth-platform` 的改动一律**只增不改**（新增端点 / 新增 zed definition），
   保证 his-platform 等既有消费方零影响。
7. SpiceDB `:8543` 写 schema 是**全量替换**，加 `oa.zed` 必须与 knowledge/his/recsys/risk 合并后再写。

## 本机现实
- **Testcontainers 在本机跑不起来** → 集成验证一律写成 bash 冒烟脚本打运行中的 compose 容器；
  需要容器的 JUnit 测试写成"Docker 不可用则 `assumeTrue` 跳过"。
- compose 起之前先 `docker compose -p oa-platform down --remove-orphans`（清 docker-proxy 残留占端口）。
- **zsh 不做无引号变量分词**：脚本里 `for x in $LIST` 会当成一个词，循环要显式列举或用数组。
- 端口 9092 属 langchain4j、29092/25432 属 workflow、15432 属 auth，**别动别人的容器**。

## 前端
PC `oa-console` 与移动端 `oa-mobile` 在**动手实现前必须先走 `/frontend-plan`**
（auth-console / workflow-console 都是这么做的）。
