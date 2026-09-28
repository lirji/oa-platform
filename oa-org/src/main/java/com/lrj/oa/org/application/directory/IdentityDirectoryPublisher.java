package com.lrj.oa.org.application.directory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.lrj.authz.protocol.DirectoryEvents;
import com.lrj.authz.protocol.DirectoryEvents.*;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.org.domain.IdentityDirectoryModels.Source;
import com.lrj.oa.org.infrastructure.mapper.IdentityDirectoryMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** OA 组织事务的目录捕获器，只落本库 Outbox，禁止在业务事务中调用 auth 或消息服务。 */
@Service
public class IdentityDirectoryPublisher {
    private static final int MAX_EVENT_BYTES = 65_536;
    private static final ObjectMapper JSON = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final IdentityDirectoryMapper mapper;
    public IdentityDirectoryPublisher(IdentityDirectoryMapper mapper) { this.mapper = mapper; }

    /** 登记接管与种子旁路互斥；未登记企业保持旧行为，已登记企业串行捕获用例最终事实。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public Source beforeWrite() {
        if (!mapper.tryCaptureGate()) { throw conflict("目录初始化或批量操作中，请重试"); }
        Source source;
        try { source = mapper.lockSource(TenantContext.get()); }
        catch (PessimisticLockingFailureException failure) { throw conflict("本企业目录变更处理中，请重试"); }
        if (source != null && !source.initialized()) { throw conflict("目录初始化尚未封存"); }
        return source;
    }
    /** 员工资料、任职、汇报和离职共用一个目录聚合版本，不依赖 employee.version。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void employee(Source source, long employeeId) {
        if (source == null) { return; }
        Payload payload = employeePayload(source, employeeId);
        appendChange(source, DirectoryAggregateType.EMPLOYEE, employeeId, payload);
    }
    /** 只发布本节点直接 parent/status，移动子树不会复制计算路径为权限关系。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void organization(Source source, long orgId) {
        if (source == null) { return; }
        Payload payload = organizationPayload(source, orgId);
        appendChange(source, DirectoryAggregateType.ORG, orgId, payload);
    }

    Payload employeePayload(Source source, long employeeId) {
        var fact = mapper.employee(source.tenantId(), employeeId);
        if (fact == null) { throw conflict("员工不属于登记的来源企业"); }
        // 沿用 OA 用例的本地业务日期；保留当前/未来关系，历史到期记录仍在 OA 权威库。
        LocalDate today = LocalDate.now();
        var assignments = mapper.assignments(source.tenantId(), employeeId, today);
        var reporting = mapper.reportingLines(source.tenantId(), employeeId, today);
        if (assignments.size() > DirectoryEvents.MAX_RELATIONS || reporting.size() > DirectoryEvents.MAX_RELATIONS) {
            throw conflict("直接目录关系超过协议上限，不能截断发布");
        }
        for (var assignment : assignments) {
            if (mapper.organization(source.tenantId(), Long.parseLong(assignment.orgId())) == null) { throw conflict("任职引用跨企业或缺失组织"); }
        }
        for (var line : reporting) {
            if (mapper.employee(source.tenantId(), Long.parseLong(line.managerEmployeeId())) == null) { throw conflict("汇报线引用跨企业或缺失员工"); }
        }
        return new Payload(new Employee(Long.toString(employeeId), fact.userId(), fact.status(), assignments, reporting), null, null);
    }
    Payload organizationPayload(Source source, long orgId) {
        var fact = mapper.organization(source.tenantId(), orgId);
        if (fact == null) { throw conflict("组织不属于登记的来源企业"); }
        if (fact.parentId() != null && mapper.organization(source.tenantId(), fact.parentId()) == null) { throw conflict("组织父节点跨企业或缺失"); }
        return new Payload(null, new Organization(Long.toString(orgId), fact.parentId() == null ? null : fact.parentId().toString(), fact.status()), null);
    }
    private void appendChange(Source source, DirectoryAggregateType type, long aggregate, Payload payload) {
        Long version = mapper.nextVersion(source.tenantId(), type.code(), aggregate);
        Long sequence = mapper.nextSequence(source.tenantId());
        if (version == null || sequence == null) { throw conflict("目录版本或序号不可继续分配"); }
        append(source, event(source, sequence, type, Long.toString(aggregate), version, null, payload));
    }
    Event event(Source source, long sequence, DirectoryAggregateType type, String aggregate, long version, String snapshot, Payload payload) {
        return new Event(DirectoryEvents.SCHEMA_VERSION, UUID.randomUUID().toString(), source.source(), source.environment(),
                Long.toString(source.tenantId()), sequence, type, aggregate, version, Instant.now().truncatedTo(ChronoUnit.MICROS).toString(),
                snapshot, payload, DirectoryEvents.payloadHash(type, payload));
    }
    void append(Source source, Event event) {
        String json = encode(event);
        if (mapper.append(source.tenantId(), event.partitionSequence(), event.eventId(), event.aggregateType().code(), event.aggregateId(),
                event.aggregateVersion(), event.snapshotId(), DirectoryEvents.eventFingerprint(event), json) != 1) { throw conflict("目录事件未能原子保存"); }
    }
    String encode(Event event) {
        try {
            String json = JSON.writeValueAsString(event);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_EVENT_BYTES) { throw conflict("目录事件超过协议上限"); }
            return json;
        } catch (JsonProcessingException failure) { throw conflict("目录事件无法序列化"); }
    }
    static BusinessException conflict(String reason) { return BusinessException.of(ResultCode.CONFLICT, reason); }
}
