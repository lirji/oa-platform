package com.lrj.oa.flow.infrastructure.approval;

import com.lrj.authz.protocol.ApprovalDtos.*;
import com.lrj.authz.protocol.ApprovalSignature;
import com.lrj.oa.flow.infrastructure.mapper.CentralApprovalMapper;
import com.lrj.oa.flow.infrastructure.workflow.WorkflowGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;

/** 只有真实引擎完成和持久办理日志同时成立才产生回调；通知异常不改审批证据。 */
@Component
@ConditionalOnProperty(name={"oa.flow.central-approval.enabled","oa.flow.central-approval.callback-enabled"},havingValue="true")
public class CentralDecisionDelivery {
    private static final String PATH="/internal/oa-approval/v1/events";
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(CentralDecisionDelivery.class);
    private final CentralApprovalMapper mapper;
    private final CentralApprovalProperties config;
    private final WorkflowGateway workflow;
    private final TransactionTemplate tx;
    private final URI callback;
    private final String key;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();

    /** 回调密钥与入站启动密钥分开，非TLS只允许loopback隔离进程。 */
    public CentralDecisionDelivery(CentralApprovalMapper mapper,CentralApprovalProperties config,WorkflowGateway workflow,
                                   PlatformTransactionManager transactions,@Value("${oa.flow.central-approval.callback-url}") String url,
                                   @Value("${oa.flow.central-approval.outbound-key}") String key) {
        this.mapper=mapper;this.config=config;this.workflow=workflow;this.tx=new TransactionTemplate(transactions);tx.setTimeout(5);
        this.callback=URI.create(url);this.key=key;ApprovalSignature.validateKey(key);
        if(!PATH.equals(callback.getPath()) || callback.getHost()==null || callback.getUserInfo()!=null || callback.getQuery()!=null || callback.getFragment()!=null
                || !("https".equals(callback.getScheme()) || config.allowLoopbackHttp() && "http".equals(callback.getScheme()) && "127.0.0.1".equals(callback.getHost())))
            throw new IllegalArgumentException("APPROVAL_CALLBACK_CONFIG_INVALID");
    }

    /** 有界取证，每次最多20条；依赖故障保持原单据，后续重新核验。 */
    @Scheduled(fixedDelayString="${oa.flow.central-approval.capture-ms:5000}")
    public void capture() {
        if(!workflow.remote()) return;
        for(var terminal:mapper.terminals()) {
            try {
                var start=CentralApprovalWire.read(terminal.payloadJson().getBytes(StandardCharsets.UTF_8),Start.class);
                if(!config.tenantId().equals(start.tenantId()) || !config.applicationId().equals(start.applicationId()) || !config.environment().equals(start.environment())) continue;
                var bridge=config.members().get(start.approverMembershipId());
                if(bridge==null || bridge.generation()!=start.approverGeneration() || !bridge.userId().equals(terminal.actorUserId()) || terminal.onBehalfOf()!=null) continue;
                if(!("APPROVE".equals(terminal.action())?"APPROVED":"REJECTED").equals(terminal.outcome())) continue;
                var process=workflow.findProcess(start.businessKey());
                if(process.isEmpty() || process.get().running() || !workflow.findTasks("oaGenericApproval",start.businessKey()).isEmpty()) continue;
                String event=UUID.randomUUID().toString();
                var decision=new Decision(event,"ACCESS_REQUEST_DECIDED",1,"oa-platform",start.tenantId(),start.applicationId(),start.environment(),
                        start.requestId(),start.requestVersion(),start.snapshotHash(),Long.toString(terminal.instanceId()),start.policyId(),start.policyVersion(),1,
                        terminal.outcome(),start.approverMembershipId(),start.approverGeneration(),terminal.actedAt().toString(),start.requestId());
                String payload=CentralApprovalWire.body(decision).toString();
                tx.executeWithoutResult(status->mapper.enqueueDecision(start.requestId(),event,payload,config.tenantId(),config.applicationId(),config.environment()));
            } catch(Exception failure) { LOG.warn("APPROVAL_EVIDENCE_UNCONFIRMED instance={}",terminal.instanceId()); }
        }
    }

    /** 每轮单条可靠投递；网络不持数据库事务，超时只能保留未知并重试相同事件。 */
    @Scheduled(fixedDelayString="${oa.flow.central-approval.delivery-ms:1000}")
    public void deliver() {
        String lease=UUID.randomUUID().toString();
        var delivery=tx.execute(status->{mapper.exhaustDecisions();return mapper.claimDecision(lease,config.tenantId(),config.applicationId(),config.environment());});
        if(delivery==null) return;
        try {
            byte[] body=delivery.payloadJson().getBytes(StandardCharsets.UTF_8);
            var request=HttpRequest.newBuilder(callback).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json")
                    .header(ApprovalSignature.HEADER,ApprovalSignature.sign(key,"oa-platform",config.environment(),PATH,body,Instant.now()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()) {
                byte[] bytes=stream.readNBytes(4097);
                if(response.statusCode()!=202 || bytes.length>4096) throw new IllegalStateException("CALLBACK_UNCONFIRMED");
                var receipt=CentralApprovalWire.read(bytes,Receipt.class);
                if(!delivery.eventId().equals(receipt.eventId()) || !java.util.Set.of("RECEIVED","APPLIED","REJECTED","IGNORED").contains(receipt.status())) throw new IllegalStateException("CALLBACK_RECEIPT_MISMATCH");
            }
            tx.executeWithoutResult(status->mapper.finishDecision(delivery.requestId(),lease));
        } catch(Exception failure) {
            if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
            tx.executeWithoutResult(status->mapper.failDecision(delivery.requestId(),lease));
            LOG.warn("APPROVAL_CALLBACK_UNCONFIRMED event={}",delivery.eventId());
        }
    }
}
