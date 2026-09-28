# P1-04 隔离来源运行说明

先安装 auth fd03979 的 protocol/SDK，再运行 `mvn -B package -DskipTests`。在专用隔离库执行 OA 原有 Flyway 迁移（含 V8）；初始化工具不自动建库或迁移。

## 受控接管

创建 0600 配置，真实凭据不提交：

```properties
jdbc.url=jdbc:postgresql://127.0.0.1:<port>/<isolated_db>
jdbc.username=<dedicated_role>
jdbc.password=<private_password>
tenant.id=<positive_oa_tenant>
source=oa
environment=test
operator=<operator_reference>
max.records=10000
```

所有写实例都升级到捕获版本后才能登记；旧写程序不能与已接管来源共存。

```sh
java -Dloader.main=com.lrj.oa.org.infrastructure.directory.IdentityDirectoryAdminCli \
  -cp oa-app/target/oa-app-0.1.0-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  initialize /private/path/directory-initialize.properties
```

仅接受隔离回环 PostgreSQL。企业/来源/环境登记不可更改；重跑不重建已提交批次。超限或锁冲突失败可以重跑，不能改历史事件、清空业务表绕过失败。

## 服务出口

`oa.identity-directory.export.enabled` 默认 false。启用时必须配置 tenant-id、source、environment、credential-sha256。凭据为至少 32 个随机字节编码的无 padding base64url；服务端仅存 SHA-256 小写摘要，原值保存在 auth 私密配置。

HTTPS 默认必需；隔离直连回环可设置 allow-loopback-http=true，非回环地址仍拒绝。代理必须正确终止可信 TLS，不信任任意转发头。

- GET `/internal/directory/v1/status`：来源、提交/确认水位和 backlog。
- GET `/internal/directory/v1/events?after_sequence=0&limit=100`：`{events:[...]}`，页长 1–100。
- POST `/internal/directory/v1/ack`：`{sequence:<positive>,fingerprint:<sha256>}`，仅在 auth 连续提交后确认。

全部接口使用 `Authorization: Bearer <service-credential>`。关闭出口不停止已登记来源捕获。确认失败保留 Outbox，下次导入补确认；不能据源端离职日志宣称跨平台撤权完成。

## 验证

`OA_DIRECTORY_TEST_CONFIG=/private/source.properties OA_AUTH_DIRECTORY_TEST_CONFIG=/private/governance.properties mvn -B verify -Pdirectory-it`。
测试仅接受专用隔离命名空间和 0600 配置，保留测试记录，不用 TRUNCATE 重置共享环境。

双库测试依赖 auth b11d080 的 governance 制品（仅 test scope），必须先安装到同一个 Maven 本地仓库。
真实 IdP 验证另设 `OA_DIRECTORY_IDENTITY_FIXTURE`（专属 casdoor.json/tokens.json 目录）与
`OA_DIRECTORY_IDENTITY_MANAGEMENT`（隔离实例管理凭据文件），全部私密文件 0600。
每次完整真实身份链验证使用新专属夹具用户；OA 的 user_id 全局唯一，不通过清空数据、改写历史员工来复用上次已离职用户。
可用 auth 仓既有 governance-casdoor-fixture.py 在固定隔离 18090 实例创建新 run 目录；不得使用共享 8000 实例或 P1-02 外部负例身份。
