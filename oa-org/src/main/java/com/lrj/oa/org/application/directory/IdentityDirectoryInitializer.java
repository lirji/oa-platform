package com.lrj.oa.org.application.directory;

import com.lrj.authz.protocol.DirectoryEvents;
import com.lrj.authz.protocol.DirectoryEvents.*;
import com.lrj.oa.org.domain.IdentityDirectoryModels.Source;
import com.lrj.oa.org.infrastructure.mapper.IdentityDirectoryMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 受控初始化只在单次有界事务中封存目录；失败整批回滚，已提交批次重试不重建。 */
@Service
public class IdentityDirectoryInitializer {
    private static final int MAX_RECORDS = 10_000;
    private static final long MAX_BYTES = 16 * 1024 * 1024;
    private static final long MAX_NANOS = java.time.Duration.ofSeconds(60).toNanos();
    private final IdentityDirectoryMapper mapper;
    private final IdentityDirectoryPublisher publisher;

    public IdentityDirectoryInitializer(IdentityDirectoryMapper mapper, IdentityDirectoryPublisher publisher) {
        this.mapper = mapper; this.publisher = publisher;
    }

    /** 固定企业/来源/环境不可修改；操作人只用于受控登记审计，不提供匿名 HTTP 入口。 */
    @Transactional(timeout = 60)
    public Source initialize(long tenant, String source, String environment, String operator, int maxRecords) {
        if (tenant < 1 || source == null || source.length() > 100 || !source.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")
                || environment == null || environment.length() > 40 || !environment.matches("[a-z0-9][a-z0-9-]*")
                || operator == null || operator.isBlank() || operator.length() > 160 || operator.chars().anyMatch(Character::isISOControl)
                || maxRecords < 1 || maxRecords > MAX_RECORDS) { throw new IllegalArgumentException("目录初始化配置无效"); }
        long started = System.nanoTime();
        if (!mapper.tryRegistrationGate()) { throw IdentityDirectoryPublisher.conflict("组织写入或批量操作中，请重试初始化"); }
        mapper.register(tenant, source, environment, operator);
        Source authority = mapper.lockSource(tenant);
        if (authority == null || !source.equals(authority.source()) || !environment.equals(authority.environment())) {
            throw IdentityDirectoryPublisher.conflict("既有来源登记不可变更");
        }
        if (authority.initialized()) { return authority; }
        var orgs = mapper.organizations(tenant, maxRecords + 1);
        var employees = mapper.employees(tenant, maxRecords + 1);
        if (orgs.size() + employees.size() > maxRecords) { throw IdentityDirectoryPublisher.conflict("初始化超过记录上限，未提交任何接管状态"); }
        String snapshot = UUID.randomUUID().toString();
        long start = sequence(tenant);
        var events = new ArrayList<Event>(orgs.size() + employees.size());
        long bytes = 0;
        MessageDigest digest = sha256();
        for (DirectoryAggregateType type : new DirectoryAggregateType[]{DirectoryAggregateType.ORG, DirectoryAggregateType.EMPLOYEE}) {
            for (long id : type == DirectoryAggregateType.ORG ? orgs : employees) {
                var payload = type == DirectoryAggregateType.ORG ? publisher.organizationPayload(authority, id) : publisher.employeePayload(authority, id);
                Long version = mapper.nextVersion(tenant, type.code(), id);
                if (version == null) { throw IdentityDirectoryPublisher.conflict("目录版本不可继续分配"); }
                Event event = publisher.event(authority, sequence(tenant), type, Long.toString(id), version, snapshot, payload);
                bytes += publisher.encode(event).getBytes(StandardCharsets.UTF_8).length;
                if (bytes > MAX_BYTES || System.nanoTime() - started > MAX_NANOS) {
                    throw IdentityDirectoryPublisher.conflict("初始化超过字节或时间上限，整批回滚");
                }
                digest.update(HexFormat.of().parseHex(DirectoryEvents.eventFingerprint(event)));
                events.add(event);
            }
        }
        long end = sequence(tenant);
        var manifest = new Payload(null, null, new Snapshot(start, end, events.size(), HexFormat.of().formatHex(digest.digest())));
        publisher.append(authority, publisher.event(authority, start, DirectoryAggregateType.SNAPSHOT_BEGIN, snapshot, 1, snapshot, manifest));
        for (Event event : events) { publisher.append(authority, event); }
        publisher.append(authority, publisher.event(authority, end, DirectoryAggregateType.SNAPSHOT_END, snapshot, 1, snapshot, manifest));
        if (mapper.initialized(tenant) != 1) { throw IdentityDirectoryPublisher.conflict("初始化封存状态冲突"); }
        return mapper.source(tenant);
    }

    private long sequence(long tenant) {
        Long value = mapper.nextSequence(tenant);
        if (value == null) { throw IdentityDirectoryPublisher.conflict("目录序号不可继续分配"); }
        return value;
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 不可用", impossible); }
    }
}
