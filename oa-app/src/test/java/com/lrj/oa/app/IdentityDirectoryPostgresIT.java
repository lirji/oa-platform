package com.lrj.oa.app;

import com.lrj.oa.org.application.directory.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.lrj.authz.protocol.DirectoryEvents;
import com.lrj.authz.protocol.DirectoryEvents.Event;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.crypto.SensitiveCrypto;
import com.lrj.oa.org.application.*;
import com.lrj.oa.org.application.command.OrgCommands.*;
import com.lrj.oa.org.domain.OrgTreeSnapshot;
import com.lrj.oa.org.infrastructure.cache.OrgTreeCache;
import com.lrj.oa.org.infrastructure.mapper.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 独立真实 PG 验证业务服务与目录捕获的提交/回滚边界，不连接共享 OA 库。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityDirectoryPostgresIT {
    private static final AtomicLong TENANTS = new AtomicLong(System.currentTimeMillis());
    private static final ObjectMapper JSON = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private IdentityDirectoryMapper directory;
    private IdentityDirectoryPublisher publisher;
    private IdentityDirectoryInitializer initializer;
    private EmployeeService employees;
    private OrgUnitService orgs;
    private OrgSeedService seed;
    private long tenant;

    @BeforeAll void openOwnedDatabase() throws Exception {
        Path file = Path.of(Objects.requireNonNull(System.getenv("OA_DIRECTORY_TEST_CONFIG")));
        assertThat(Files.isSymbolicLink(file)).isFalse();
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
        String url = properties.getProperty("jdbc.url");
        assertThat(url).matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/auth_gov_p1_test_[a-z0-9_]+");
        dataSource = new DriverManagerDataSource(url, properties.getProperty("jdbc.username"), properties.getProperty("jdbc.password"));
        jdbc = new JdbcTemplate(dataSource);
        // 生产 V1 与组织模块迁移原文，不用测试建表替代真实数据库语义。
        Flyway.configure().dataSource(dataSource).locations("filesystem:src/main/resources/db/migration", "filesystem:../oa-org/src/main/resources/db/migration").load().migrate();
        transactions = new DataSourceTransactionManager(dataSource);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (Class<?> type : List.of(IdentityDirectoryMapper.class, EmployeeMapper.class, AssignmentMapper.class,
                ReportingLineMapper.class, OrgUnitMapper.class, OrgClosureMapper.class)) { config.addMapper(type); }
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(dataSource); factory.setConfiguration(config);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/directory/*.xml"));
        var provider = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        provider.registerSingleton("directoryTestHandler", new com.lrj.oa.iam.infrastructure.datascope.OaDataPermissionHandler());
        var plugins = new com.lrj.oa.common.config.MybatisPlusConfig().mybatisPlusInterceptor(
                provider.getBeanProvider(com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler.class));
        factory.setPlugins(plugins);
        var session = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        directory = session.getMapper(IdentityDirectoryMapper.class);
        publisher = proxy(new IdentityDirectoryPublisher(directory));
        initializer = proxy(new IdentityDirectoryInitializer(directory, publisher));
        ApplicationEventPublisher events = event -> {};
        var tree = mock(OrgTreeCache.class); var snapshot = mock(OrgTreeSnapshot.class);
        when(tree.snapshot()).thenReturn(snapshot); when(snapshot.pathOf(anyLong())).thenReturn("/test/");
        employees = proxy(new EmployeeService(session.getMapper(EmployeeMapper.class), session.getMapper(AssignmentMapper.class),
                session.getMapper(ReportingLineMapper.class), session.getMapper(OrgUnitMapper.class),
                new SensitiveCrypto(Base64.getEncoder().encodeToString(new byte[32])), events, tree,
                (permission, org, path, owner) -> true, publisher));
        orgs = proxy(new OrgUnitService(session.getMapper(OrgUnitMapper.class), session.getMapper(OrgClosureMapper.class), events, publisher));
        seed = new OrgSeedService(dataSource, session.getMapper(OrgUnitMapper.class), events);
    }
    @BeforeEach void scope() { tenant = TENANTS.incrementAndGet(); TenantContext.set(tenant); }
    @AfterEach void clear() { TenantContext.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @Test void initializationSealsExactSnapshotAndReplayDoesNotReset() throws Exception {
        long root = org(null); long employee = employee(root);
        var source = initializer.initialize(tenant, "oa", "test", "directory-it", 100);
        assertThat(source.lastSequence()).isEqualTo(4); assertThat(source.initialized()).isTrue();
        List<Event> events = events();
        var digest = MessageDigest.getInstance("SHA-256");
        for (Event event : events.subList(1, 3)) { digest.update(HexFormat.of().parseHex(DirectoryEvents.eventFingerprint(event))); }
        assertThat(events.getFirst().payload().snapshot().contentHash()).isEqualTo(HexFormat.of().formatHex(digest.digest()));
        assertThat(events.getFirst().payload()).isEqualTo(events.getLast().payload());
        assertThat(events.get(2).payload().employee().employeeId()).isEqualTo(Long.toString(employee));
        assertThat(events.get(2).payload().employee().assignments()).hasSize(1);
        assertThat(initializer.initialize(tenant, "oa", "test", "repeat-it", 100).lastSequence()).isEqualTo(4);
        assertThatThrownBy(() -> initializer.initialize(tenant, "oa", "changed", "it", 100)).isInstanceOf(RuntimeException.class);
        assertThat(events()).isEqualTo(events);
    }

    @Test void everyEmployeeWriteCapturesFinalFactsAndLeave() throws Exception {
        initializer.initialize(tenant, "oa", "test", "it", 100);
        long a = org(null), b = org(null), manager = employee(a), person = employee(a);
        employees.update(person, new UpdateEmployee("changed", null, null, null, null, null, null));
        long assignment = employees.addAssignment(person, new AddAssignment(b, null, "CONCURRENT", true, LocalDate.now()));
        employees.closeAssignment(assignment, LocalDate.now());
        employees.transfer(person, new TransferEmployee(b, null, LocalDate.now(), false, "test"));
        employees.setReportingLine(person, new SetReportingLine(manager, "SOLID", LocalDate.now()));
        employees.leave(person, LocalDate.now());
        var changes = events().stream().filter(e -> e.aggregateId().equals(Long.toString(person))
                && e.aggregateType() == DirectoryEvents.DirectoryAggregateType.EMPLOYEE).toList();
        assertThat(changes).hasSize(7);
        assertThat(changes.stream().map(Event::aggregateVersion)).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(changes.getLast().payload().employee().status()).isEqualTo("LEFT");
        assertThat(changes.getLast().payload().employee().assignments()).isEmpty();
        assertThat(changes.getLast().payload().employee().reportingLines()).isEmpty();
        assertThat(events().stream().map(Event::partitionSequence)).containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(1, directory.source(tenant).lastSequence()).boxed().toList());
    }

    @Test void organizationMoveUpdateDissolveAndCycleRejection() throws Exception {
        initializer.initialize(tenant, "oa", "test", "it", 100);
        long root = org(null), child = org(root), other = org(null);
        long before = directory.source(tenant).lastSequence();
        assertThatThrownBy(() -> orgs.move(root, child)).isInstanceOf(RuntimeException.class);
        assertThat(directory.source(tenant).lastSequence()).isEqualTo(before);
        orgs.move(child, other);
        orgs.update(child, new UpdateOrg("renamed", null, null, null, null, null, null, null));
        orgs.dissolve(child);
        var changes = events().stream().filter(e -> e.aggregateType() == DirectoryEvents.DirectoryAggregateType.ORG
                && e.aggregateId().equals(Long.toString(child))).toList();
        assertThat(changes).hasSize(4);
        assertThat(changes.get(1).payload().organization().parentId()).isEqualTo(Long.toString(other));
        assertThat(changes.getLast().payload().organization().status()).isEqualTo("DISSOLVED");
    }

    @Test void outboxFailureRollsBackBusinessVersionAndSequence() throws Exception {
        initializer.initialize(tenant, "oa", "test", "it", 100);
        long person = employee(org(null)), before = directory.source(tenant).lastSequence();
        String trigger = "test_directory_fail_" + tenant;
        jdbc.execute("CREATE FUNCTION oa_org." + trigger + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test fault'; END $$");
        jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON oa_org.identity_directory_outbox FOR EACH ROW WHEN (NEW.tenant_id = " + tenant + ") EXECUTE FUNCTION oa_org." + trigger + "()");
        try { assertThatThrownBy(() -> employees.leave(person, LocalDate.now())).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("DROP TRIGGER " + trigger + " ON oa_org.identity_directory_outbox"); jdbc.execute("DROP FUNCTION oa_org." + trigger + "()"); }
        assertThat(jdbc.queryForObject("SELECT status FROM oa_org.employee WHERE id=?", String.class, person)).isEqualTo("PROBATION");
        assertThat(directory.source(tenant).lastSequence()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oa_org.employee_org_assignment WHERE employee_id=? AND valid_to IS NULL", Long.class, person)).isEqualTo(1);
        employees.leave(person, LocalDate.now());
        assertThat(events().getLast().aggregateVersion()).isEqualTo(2);
        assertThat(directory.source(tenant).lastSequence()).isEqualTo(before + 1);
    }

    @Test void overflowSnapshotRollsBackRegistrationAndManagedSourceRejectsBulk() {
        org(null); org(null);
        assertThatThrownBy(() -> initializer.initialize(tenant, "oa", "test", "it", 1)).isInstanceOf(RuntimeException.class);
        assertThat(directory.source(tenant)).isNull();
        initializer.initialize(tenant, "oa", "test", "it", 100);
        long count = jdbc.queryForObject("SELECT count(*) FROM oa_org.org_unit", Long.class);
        assertThatThrownBy(() -> seed.seed(1, 1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seed.truncateAll()).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oa_org.org_unit", Long.class)).isEqualTo(count);
    }

    @Test void registrationAndUnregisteredWritesShareMutexWithoutCheckThenActGap() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> writer = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                assertThat(directory.tryCaptureGate()).isTrue(); entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("test timeout"); } }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            try { assertThatThrownBy(() -> initializer.initialize(tenant, "oa", "test", "it", 100)).isInstanceOf(RuntimeException.class); }
            finally { release.countDown(); }
            writer.get(5, TimeUnit.SECONDS);
        }
        assertThat(directory.source(tenant)).isNull();
        assertThat(initializer.initialize(tenant, "oa", "test", "it", 100).lastSequence()).isEqualTo(2);
    }

    @Test void crossTenantParentAndAssignmentFailWithoutPartialWrites() {
        long foreign = org(null); TenantContext.set(TENANTS.incrementAndGet()); tenant = TenantContext.get();
        initializer.initialize(tenant, "oa", "test", "it", 100);
        long before = directory.source(tenant).lastSequence();
        assertThatThrownBy(() -> org(foreign)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> employee(foreign)).isInstanceOf(RuntimeException.class);
        assertThat(directory.source(tenant).lastSequence()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oa_org.employee WHERE tenant_id=?", Long.class, tenant)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oa_org.org_unit WHERE tenant_id=?", Long.class, tenant)).isZero();
    }

    @Test void rejectedLeaveUpdateCannotCommitClosedRelationshipsOrEvent() {
        initializer.initialize(tenant, "oa", "test", "it", 100);
        long person = employee(org(null)), before = directory.source(tenant).lastSequence();
        String trigger = "test_directory_skip_" + tenant;
        jdbc.execute("CREATE FUNCTION oa_org." + trigger + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NULL; END $$");
        jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE UPDATE ON oa_org.employee FOR EACH ROW WHEN (NEW.tenant_id = " + tenant + ") EXECUTE FUNCTION oa_org." + trigger + "()");
        try { assertThatThrownBy(() -> employees.leave(person, LocalDate.now())).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("DROP TRIGGER " + trigger + " ON oa_org.employee"); jdbc.execute("DROP FUNCTION oa_org." + trigger + "()"); }
        assertThat(jdbc.queryForObject("SELECT status FROM oa_org.employee WHERE id=?", String.class, person)).isEqualTo("PROBATION");
        assertThat(directory.source(tenant).lastSequence()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oa_org.employee_org_assignment WHERE employee_id=? AND valid_to IS NULL", Long.class, person)).isEqualTo(1);
    }

    @Test void concurrentWriterCannotPublishPastUncommittedSourceThenRetryHasNoGap() throws Exception {
        initializer.initialize(tenant, "oa", "test", "it", 100);
        long tenantId = tenant, person = employee(org(null)), before = directory.source(tenant).lastSequence();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> writer = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                TenantContext.set(tenantId);
                try {
                    var source = publisher.beforeWrite();
                    publisher.employee(source, person); entered.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("test timeout"); }
                    status.setRollbackOnly();
                } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                finally { TenantContext.clear(); }
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(directory.source(tenant).lastSequence()).isEqualTo(before);
                assertThatThrownBy(() -> employees.leave(person, LocalDate.now())).isInstanceOf(RuntimeException.class);
            } finally { release.countDown(); }
            writer.get(5, TimeUnit.SECONDS);
        }
        employees.leave(person, LocalDate.now());
        assertThat(directory.source(tenant).lastSequence()).isEqualTo(before + 1);
        assertThat(events().getLast().aggregateVersion()).isEqualTo(2);
    }

    @Test void copyGuardAndInitializerUseSameDatabaseMutex() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT pg_advisory_xact_lock(7140310401)");
            }
            assertThatThrownBy(() -> initializer.initialize(tenant, "oa", "test", "it", 100)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> seed.seed(1, 1)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> seed.truncateAll()).isInstanceOf(IllegalStateException.class);
            connection.rollback();
        }
        assertThat(directory.source(tenant)).isNull();
    }

    @Test void relationOverflowFailsAtomicallyRatherThanTruncating() {
        initializer.initialize(tenant, "oa", "test", "it", 100);
        long department = org(null), person = employee(department), before = directory.source(tenant).lastSequence();
        // 夹具直写仅用于构造超过公开用例边界的已有数据，产品仍必须拒绝发布残缺聚合。
        jdbc.update("INSERT INTO oa_org.employee_org_assignment(tenant_id, employee_id, org_unit_id, assignment_type, valid_from) SELECT ?, ?, ?, 'DOTTED', current_date FROM generate_series(1,100)", tenant, person, department);
        assertThatThrownBy(() -> employees.update(person, new UpdateEmployee("not-committed", null, null, null, null, null, null))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT name FROM oa_org.employee WHERE id=?", String.class, person)).isEqualTo("directory-it");
        assertThat(directory.source(tenant).lastSequence()).isEqualTo(before);
    }

    @Test void exportAndConfirmationStayWithinRegisteredSourceAndNeverRegress() throws Exception {
        initializer.initialize(tenant, "oa", "test", "it", 100);
        employee(org(null));
        var config = new com.lrj.oa.org.infrastructure.directory.IdentityDirectoryExportProperties(true, tenant, "oa", "test", "a".repeat(64), true);
        var exporter = proxy(new IdentityDirectoryExport(directory, config));
        assertThatThrownBy(exporter::status).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        serviceIdentity(tenant);
        long last = directory.source(tenant).lastSequence();
        assertThat(exporter.status().backlog()).isEqualTo(last);
        assertThat(exporter.events(0, 2).events()).hasSize(2);
        assertThat(exporter.events(last, 100).events()).isEmpty();
        assertThatThrownBy(() -> exporter.events(last + 1, 1)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> exporter.events(0, 101)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        String hash = directory.fingerprint(tenant, last);
        assertThat(exporter.acknowledge(last, hash).backlog()).isZero();
        assertThat(exporter.acknowledge(last, hash).ackedSequence()).isEqualTo(last);
        assertThat(exporter.acknowledge(1, directory.fingerprint(tenant, 1)).ackedSequence()).isEqualTo(last);
        assertThatThrownBy(() -> exporter.acknowledge(last, "0".repeat(64))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> exporter.acknowledge(last + 1, hash)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        serviceIdentity(tenant + 1);
        assertThatThrownBy(exporter::status).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(directory.source(tenant).ackedSequence()).isEqualTo(last);
    }

    @Test void controlledCliIsIdempotentAndRejectsUnsafePrivateFile() throws Exception {
        long root = org(null); employee(root);
        Path file = Files.createTempFile("oa-directory-it-", ".properties", java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
        try {
            Properties config = new Properties();
            try (var reader = Files.newBufferedReader(Path.of(System.getenv("OA_DIRECTORY_TEST_CONFIG")))) { config.load(reader); }
            config.setProperty("tenant.id", Long.toString(tenant)); config.setProperty("source", "oa");
            config.setProperty("environment", "test"); config.setProperty("operator", "directory-it");
            try (var writer = Files.newBufferedWriter(file)) { config.store(writer, "isolated test configuration"); }
            String[] command = {"initialize", file.toString()};
            assertThat(com.lrj.oa.org.infrastructure.directory.IdentityDirectoryAdminCli.run(command, new java.io.PrintWriter(new java.io.StringWriter()), new java.io.PrintWriter(new java.io.StringWriter()))).isZero();
            long checkpoint = directory.source(tenant).lastSequence();
            assertThat(com.lrj.oa.org.infrastructure.directory.IdentityDirectoryAdminCli.run(command, new java.io.PrintWriter(new java.io.StringWriter()), new java.io.PrintWriter(new java.io.StringWriter()))).isZero();
            assertThat(directory.source(tenant).lastSequence()).isEqualTo(checkpoint);
            Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
            assertThat(com.lrj.oa.org.infrastructure.directory.IdentityDirectoryAdminCli.run(command, new java.io.PrintWriter(new java.io.StringWriter()), new java.io.PrintWriter(new java.io.StringWriter()))).isEqualTo(2);
            assertThat(directory.source(tenant).lastSequence()).isEqualTo(checkpoint);
        } finally { Files.deleteIfExists(file); }
    }

    private void serviceIdentity(long tenant) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        new com.lrj.oa.security.context.ServiceIdentity("oa-directory", tenant), null, List.of()));
    }

    @Test void realOaHttpAndIndependentAuthDatabaseRecoverAckLossThenRevokeOnLeave() throws Exception {
        directoryEndToEnd(null);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "OA_DIRECTORY_IDENTITY_FIXTURE", matches = ".+")
    void realCasdoorTokenLosesOaMembershipAfterSourceLeaveWhileTokenRemainsValid() throws Exception {
        Path fixturePath = Path.of(System.getenv("OA_DIRECTORY_IDENTITY_FIXTURE"));
        var fixture = privateJson(fixturePath.resolve("casdoor.json"));
        var tokens = privateJson(fixturePath.resolve("tokens.json"));
        var operations = privateJson(Path.of(Objects.requireNonNull(System.getenv("OA_DIRECTORY_IDENTITY_MANAGEMENT"))));
        assertThat(fixture.path("organization").asText()).matches("gov-p1-[a-f0-9]{12}");
        var client = fixture.path("clients").path("management");
        var authority = new com.lrj.authz.governance.authentication.TokenAuthority("http://localhost:18090",
                java.net.URI.create("http://localhost:18090/.well-known/jwks"), client.path("name").asText(), client.path("name").asText(),
                client.path("secret").asText(), operations.path("client_id").asText(), operations.path("client_secret").asText(), 2000, 2000, 4);
        var verifier = new com.lrj.authz.governance.authentication.CasdoorAccessTokenVerifier(authority);
        String access = tokens.path("management").path("access_token").asText();
        String subject = fixture.path("users").path("internal").path("id").asText();
        assertThat(verifier.verify(access).subject()).isEqualTo(subject);
        assertThatThrownBy(() -> verifier.verify(tokens.path("management").path("id_token").asText()))
                .isInstanceOf(com.lrj.authz.governance.application.GovernanceException.class);
        directoryEndToEnd(new DirectoryIdentityFixture(verifier, subject, access));
    }

    private void directoryEndToEnd(DirectoryIdentityFixture identity) throws Exception {
        long department = org(null), person = identity == null ? employee(department) : employees.create(new CreateEmployee(identity.subject(),
                UUID.randomUUID().toString().substring(0, 20), "directory-idp-it", null, null, null, null, null, null, null, null, department, null, null));
        String subject = jdbc.queryForObject("SELECT user_id FROM oa_org.employee WHERE id=?", String.class, person);
        initializer.initialize(tenant, "oa", "test", "directory-e2e", 100);
        String privateConfig = Objects.requireNonNull(System.getenv("OA_AUTH_DIRECTORY_TEST_CONFIG"), "dual database configuration required");
        var authConfig = com.lrj.authz.governance.application.GovernanceConfigurationFile.read(privateConfig);
        assertThat(authConfig.getProperty("jdbc.url")).isNotEqualTo(dataSource.getUrl())
                .matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/auth_gov_p1_test_[a-z0-9_]+");
        String credential = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        var settings = new com.lrj.oa.org.infrastructure.directory.IdentityDirectoryExportProperties(true, tenant, "oa", "test",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(credential.getBytes(java.nio.charset.StandardCharsets.US_ASCII))), true);
        var exporter = proxy(new IdentityDirectoryExport(directory, settings));
        var fault = new java.util.concurrent.atomic.AtomicBoolean(true);
        var application = new org.springframework.boot.SpringApplication(DirectoryHttpHost.class);
        application.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        application.setDefaultProperties(Map.of("server.address", "127.0.0.1", "server.port", "0", "oa.security.mode", "DEV",
                "spring.main.log-startup-info", "false", "server.forward-headers-strategy", "NONE"));
        application.addInitializers(context -> {
            context.getBeanFactory().registerSingleton("directoryExportForTest", exporter);
            context.getBeanFactory().registerSingleton("directoryLostAckForTest", fault);
        });
        try (var auth = com.lrj.authz.governance.persistence.GovernanceRuntime.open(com.lrj.authz.governance.persistence.GovernanceDatabase.from(authConfig), true);
             var host = application.run("--spring.config.name=directory-e2e-test", "--oa.security.mode=DEV", "--server.port=0", "--server.address=127.0.0.1", "--oa.identity-directory.export.enabled=true", "--oa.identity-directory.export.tenant-id=" + tenant,
                     "--oa.identity-directory.export.source=oa", "--oa.identity-directory.export.environment=test",
                     "--oa.identity-directory.export.credential-sha256=" + settings.credentialSha256(), "--oa.identity-directory.export.allow-loopback-http=true")) {
            int port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) host).getWebServer().getPort();
            String targetTenant = UUID.randomUUID().toString(), seedMember = UUID.randomUUID().toString(), seedSubject = UUID.randomUUID().toString();
            String issuer = identity == null ? "https://directory-e2e.example" : "http://localhost:18090";
            var reader = identity == null ? null : new com.lrj.authz.governance.authentication.AuthenticatedIdentityReader(identity.verifier(), auth.identity());
            auth.identity().bootstrapEmployee(new com.lrj.authz.governance.application.BootstrapCommand(UUID.randomUUID().toString(), "directory-e2e",
                    targetTenant, "e2e-" + targetTenant, UUID.randomUUID().toString(), issuer, seedSubject, seedMember,
                    java.time.Instant.parse("2020-01-01T00:00:00Z"), null, "test-seed", targetTenant, "seed"));
            var authority = new com.lrj.authz.governance.application.DirectoryAuthority(UUID.randomUUID().toString(), "oa", "test", Long.toString(tenant), targetTenant, issuer);
            var pullConfig = new com.lrj.authz.governance.application.DirectoryPullConfiguration(authority,
                    java.net.URI.create("http://127.0.0.1:" + port + "/internal/directory/v1"), credential, "directory-e2e", 2, 3000);
            try (var importer = new com.lrj.authz.governance.application.DirectoryPullImporter(auth.directory(), pullConfig)) {
                importer.register();
                assertThatThrownBy(importer::pull).isInstanceOf(com.lrj.authz.governance.application.GovernanceException.class);
                // HTTP 失败之前两库都已提交；源端确认丢失不触发回滚或重新生成消息。
                assertThat(auth.directory().checkpoint(authority).lastSequence()).isEqualTo(2);
                assertThat(directory.source(tenant).ackedSequence()).isEqualTo(2);
                assertThatThrownBy(() -> auth.identity().contextForLogin(issuer, subject, targetTenant, 1L)).isInstanceOf(com.lrj.authz.governance.application.GovernanceException.class);
                assertThat(auth.identity().membershipsForLogin(issuer, seedSubject)).extracting("id").contains(seedMember);
                assertThat(importer.pull().lastSequence()).isEqualTo(4);
                var joined = auth.identity().contextForLogin(issuer, subject, targetTenant, 1L);
                assertThat(joined.membershipId()).isNotBlank();
                if (reader != null) { assertThat(reader.memberships(identity.accessToken())).extracting("id").contains(joined.membershipId()); }
                assertThat(directory.source(tenant).ackedSequence()).isEqualTo(4);
                employees.leave(person, LocalDate.now());
                assertThat(directory.source(tenant).lastSequence()).isEqualTo(5);
                assertThat(directory.source(tenant).ackedSequence()).isEqualTo(4);
                assertThat(importer.pull().lastSequence()).isEqualTo(5);
                assertThat(auth.identity().membershipsForLogin(issuer, subject)).extracting("id").doesNotContain(joined.membershipId());
                if (reader != null) {
                    assertThat(identity.verifier().verify(identity.accessToken()).subject()).isEqualTo(subject);
                    assertThat(reader.memberships(identity.accessToken())).extracting("id").doesNotContain(joined.membershipId());
                }
                assertThatThrownBy(() -> auth.identity().contextForLogin(issuer, subject, targetTenant, 1L)).isInstanceOf(com.lrj.authz.governance.application.GovernanceException.class);
                assertThat(directory.source(tenant).ackedSequence()).isEqualTo(5);
                assertThat(importer.pull().processed()).isZero();
                assertThat(auth.identity().membershipsForLogin(issuer, seedSubject)).extracting("id").contains(seedMember);
            }
        }
    }

    private record DirectoryIdentityFixture(com.lrj.authz.governance.authentication.CasdoorAccessTokenVerifier verifier, String subject, String accessToken) {
        @Override public String toString() { return "DirectoryIdentityFixture[redacted]"; }
    }
    private com.fasterxml.jackson.databind.JsonNode privateJson(Path file) throws Exception {
        assertThat(Files.isSymbolicLink(file)).isFalse();
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        return JSON.readTree(Files.readString(file));
    }

    /** 仅测试宿主：真实 Tomcat/MVC/安全链/Controller，排除所有无关基础设施自动连接。 */
    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class,
            org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class})
    @org.springframework.context.annotation.Import({com.lrj.oa.security.config.OaSecurityConfig.class,
            com.lrj.oa.org.infrastructure.directory.IdentityDirectorySecurityConfiguration.class,
            com.lrj.oa.org.api.IdentityDirectoryController.class,
            com.lrj.oa.iam.aspect.IamWebMvcConfig.class, com.lrj.oa.iam.aspect.UnannotatedHandlerGuard.class,
            com.lrj.oa.common.web.GlobalExceptionHandler.class})
    static class DirectoryHttpHost {
        /** 故障只存在测试制品：源确认事务已结束，但向消费者返回 503，证明重试窗口。 */
        @org.springframework.context.annotation.Bean
        org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter> loseAckResponse(
                java.util.concurrent.atomic.AtomicBoolean fault) {
            var filter = new org.springframework.web.filter.OncePerRequestFilter() {
                @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                        jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, java.io.IOException {
                    var wrapper = new org.springframework.web.util.ContentCachingResponseWrapper(response);
                    chain.doFilter(request, wrapper);
                    if (request.getRequestURI().endsWith("/ack") && wrapper.getStatus() == 200 && fault.getAndSet(false)) {
                        response.reset(); response.setStatus(503); response.getWriter().write("{\"error\":\"test_lost_ack_response\"}");
                    } else { wrapper.copyBodyToResponse(); }
                }
            };
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter>(filter);
            registration.addUrlPatterns("/internal/directory/v1/*"); registration.setOrder(-101); return registration;
        }
    }

    private long org(Long parent) { return orgs.create(new CreateOrg(parent, UUID.randomUUID().toString(), "directory-it", null, "DEPT", null, null, null, null)); }
    private long employee(long org) {
        return employees.create(new CreateEmployee(UUID.randomUUID().toString(), UUID.randomUUID().toString().substring(0, 20),
                "directory-it", null, null, null, null, null, null, null, null, org, null, null));
    }
    private List<Event> events() throws Exception {
        List<Event> events = new ArrayList<>();
        for (var row : directory.events(tenant, 0, 100)) { events.add(JSON.readValue(row.eventJson(), Event.class)); }
        return events;
    }
    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
}
