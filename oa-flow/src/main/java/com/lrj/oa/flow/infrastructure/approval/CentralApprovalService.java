package com.lrj.oa.flow.infrastructure.approval;

import com.fasterxml.jackson.databind.*;
import com.lrj.authz.protocol.ApprovalDtos.*;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.flow.application.ApprovalService;
import com.lrj.oa.flow.infrastructure.mapper.CentralApprovalMapper;
import com.lrj.oa.security.context.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** 仅复用既有oa-flow编排和Outbox；普通限时申请不进入旧IAM的JIT授予逻辑。 */
@Service
@ConditionalOnProperty(name="oa.flow.central-approval.enabled",havingValue="true")
public class CentralApprovalService {
    public static final String BUSINESS_TYPE="CENTRAL_ACCESS";
    private static final ObjectMapper JSON=new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final CentralApprovalMapper mapper;
    private final CentralApprovalProperties config;
    private final ApprovalService approvals;

    /** 配置与映射都是本服务权威，申请消息不能覆盖其中的身份归属。 */
    public CentralApprovalService(CentralApprovalMapper mapper,CentralApprovalProperties config,ApprovalService approvals) {
        this.mapper=mapper;this.config=config;this.approvals=approvals;
    }

    /** 映射占位、真实OA实例和原有启动Outbox同事务；并发只产生一个实例。 */
    @Transactional
    public Instance start(Start command) {
        partition(command.tenantId(),command.applicationId(),command.environment());
        binding(command.requestId(),command.requestVersion(),command.snapshotHash());
        var requester=bridge(command.membershipId(),command.generation());
        var approver=bridge(command.approverMembershipId(),command.approverGeneration());
        if(requester.userId().equals(approver.userId()) || command.reason()==null || command.reason().isBlank() || command.reason().length()>1000
                || command.capabilities()==null || command.capabilities().isEmpty() || command.capabilities().size()>200
                || command.scopeRule()==null || command.policyVersion()<1 || command.policyHash()==null
                || !command.policyHash().matches("[a-f0-9]{64}")) throw bad();
        try {
            if(!Instant.parse(command.validTo()).isAfter(Instant.parse(command.validFrom()))) throw bad();
            String payload=JSON.writeValueAsString(command),hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
            mapper.reserve(command.requestId(),command.requestVersion(),command.snapshotHash(),hash,payload);
            var old=mapper.lock(command.requestId());
            if(old==null || !old.payloadHash().equals(hash)) throw new ResponseStatusException(HttpStatus.CONFLICT,"APPROVAL_PAYLOAD_CONFLICT");
            if(old.instanceId()!=null) return instance(old);
            // 服务身份只在此显式映射成申请快照上下文，finally恢复，避免线程复用串租户。
            UserContext previous=UserContextHolder.peek();Long previousTenant=TenantContext.get();
            try {
                TenantContext.set(config.oaTenantId());
                UserContextHolder.set(new UserContext(requester.userId(),requester.userId(),requester.employeeId(),requester.orgId(),requester.orgPath(),config.oaTenantId()));
                Long id=approvals.submit(new ApprovalService.SubmitRequest(BUSINESS_TYPE,command.businessKey(),"oaGenericApproval",
                        "central-access-v1",Map.of("snapshot",payload),"应用权限申请","待审批",List.of(approver.userId())));
                if(mapper.bind(command.requestId(),id)!=1) throw new IllegalStateException("APPROVAL_BIND_CONFLICT");
                return new Instance(command.requestId(),command.requestVersion(),command.snapshotHash(),id.toString());
            } finally {
                if(previous==null) UserContextHolder.clear(); else UserContextHolder.set(previous);
                TenantContext.set(previousTenant);
            }
        } catch(java.io.IOException | java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException("APPROVAL_ENCODING_FAILED",failure); }
    }

    /** 超时后的业务键查询必须核对完整分区和原快照。 */
    public Instance lookup(Lookup query) {
        partition(query.tenantId(),query.applicationId(),query.environment());
        binding(query.requestId(),query.requestVersion(),query.snapshotHash());
        var row=mapper.find(query.requestId());
        if(row==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(row.requestVersion()!=query.requestVersion() || !row.snapshotHash().equals(query.snapshotHash()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"APPROVAL_SNAPSHOT_CONFLICT");
        if(row.instanceId()==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        return instance(row);
    }

    private CentralApprovalProperties.Bridge bridge(String id,long generation) {
        var bridge=config.members().get(id);
        if(bridge==null || bridge.generation()!=generation) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"APPROVAL_BRIDGE_MISSING");
        return bridge;
    }
    private void partition(String tenant,String app,String environment) {
        if(!config.tenantId().equals(tenant) || !config.applicationId().equals(app) || !config.environment().equals(environment))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"APPROVAL_PARTITION_MISMATCH");
    }
    private static void binding(String id,long version,String hash) {
        try { if(!UUID.fromString(id).toString().equals(id) || version!=1 || !hash.matches("[a-f0-9]{64}")) throw bad(); }
        catch(IllegalArgumentException | NullPointerException invalid) { throw bad(); }
    }
    private static Instance instance(CentralApprovalMapper.Mapping row) {
        return new Instance(row.requestId(),row.requestVersion(),row.snapshotHash(),row.instanceId().toString());
    }
    private static ResponseStatusException bad() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"APPROVAL_INVALID"); }
}
