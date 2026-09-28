package com.lrj.oa.org.application.directory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lrj.oa.org.domain.IdentityDirectoryModels.Source;
import com.lrj.oa.org.infrastructure.mapper.IdentityDirectoryMapper;
import com.lrj.oa.org.infrastructure.directory.IdentityDirectoryExportProperties;
import com.lrj.oa.org.infrastructure.directory.IdentityDirectorySecurityConfiguration;
import com.lrj.oa.security.context.ServiceIdentity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 目录服务只读取固定来源的持久化事件；确认表示 auth 连续提交收据，不改变 OA 员工状态。 */
@Service
public class IdentityDirectoryExport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final IdentityDirectoryMapper mapper;
    private final IdentityDirectoryExportProperties config;
    public IdentityDirectoryExport(IdentityDirectoryMapper mapper, IdentityDirectoryExportProperties config) { this.mapper = mapper; this.config = config; }

    /** 运行状态以源端最后提交与目标确认的差额表达，不宣称源事务等于跨平台撤权。 */
    public record Status(String source, String environment, @JsonProperty("source_tenant_ref") String sourceTenantRef,
                         @JsonProperty("last_sequence") long lastSequence, @JsonProperty("acked_sequence") long ackedSequence,
                         @JsonProperty("acked_fingerprint") String ackedFingerprint, long backlog) {}
    /** 传输不重生成 UUID、时间、版本或摘要。 */
    public record Page(List<JsonNode> events) { public Page { events = List.copyOf(events); } }

    /** 固定范围独立于请求 tenant header、个人数据权限和 isAdmin。 */
    @Transactional(readOnly = true, timeout = 5)
    public Status status() { return status(authority()); }

    /** 最多返回 100 个不可变事件，游标稳定且允许重复拉取。 */
    @Transactional(readOnly = true, timeout = 5)
    public Page events(long after, int limit) {
        if (after < 0 || limit < 1 || limit > 100) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        Source source = authority();
        if (after > source.lastSequence()) { throw new ResponseStatusException(HttpStatus.CONFLICT); }
        List<JsonNode> events = new ArrayList<>();
        for (var event : mapper.events(source.tenantId(), after, limit)) {
            try { events.add(JSON.readTree(event.eventJson())); }
            catch (java.io.IOException error) { throw new IllegalStateException("持久化目录事件无法读取"); }
        }
        return new Page(events);
    }

    /** 同序号/摘要重试幂等，历史确认不回退水位，未知事件或异摘要拒绝。 */
    @Transactional(timeout = 5)
    public Status acknowledge(long sequence, String fingerprint) {
        if (sequence < 1 || fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        Source source = authority();
        if (!fingerprint.equals(mapper.fingerprint(source.tenantId(), sequence))) { throw new ResponseStatusException(HttpStatus.CONFLICT); }
        mapper.acknowledge(source.tenantId(), sequence, fingerprint);
        return status(mapper.source(source.tenantId()));
    }

    private Source authority() {
        var identity = ServiceIdentity.require(IdentityDirectorySecurityConfiguration.SERVICE);
        if (!config.enabled() || identity.tenantId() != config.tenantId()) { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
        Source source = mapper.source(config.tenantId());
        if (source == null || !source.initialized() || !source.source().equals(config.source()) || !source.environment().equals(config.environment())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return source;
    }
    private Status status(Source source) {
        return new Status(source.source(), source.environment(), Long.toString(source.tenantId()), source.lastSequence(),
                source.ackedSequence(), source.ackedFingerprint(), source.lastSequence() - source.ackedSequence());
    }
}
