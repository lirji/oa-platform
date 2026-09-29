package com.lrj.oa.flow.infrastructure.approval;

import com.lrj.authz.protocol.ApprovalSignature;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

/** 精确分区和身份桥来自受控配置，不能从外部申请的显示名猜测OA成员。 */
@ConfigurationProperties("oa.flow.central-approval")
public record CentralApprovalProperties(boolean enabled,String tenantId,String applicationId,String environment,
                                        long oaTenantId,String inboundKey,boolean allowLoopbackHttp,Map<String,Bridge> members) {
    /** 禁用时无须密钥；启用必须完整配置且身份桥有界。 */
    public CentralApprovalProperties {
        members=members==null?Map.of():Map.copyOf(members);
        if(enabled) {
            ApprovalSignature.validateKey(inboundKey);
            if(tenantId==null || !tenantId.matches("[a-f0-9-]{36}") || applicationId==null
                    || !applicationId.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}") || environment==null
                    || !environment.matches("[a-z0-9][a-z0-9-]{0,39}") || oaTenantId<1 || members.isEmpty() || members.size()>1000)
                throw new IllegalArgumentException("CENTRAL_APPROVAL_CONFIG_INVALID");
        }
    }
    /** 同一中央成员代际只指向受控OA主体和组织，外部成员不获得工作台登录权。 */
    public record Bridge(long generation,String userId,Long employeeId,long orgId,String orgPath) {
        public Bridge {
            if(generation<1 || userId==null || !userId.matches("[A-Za-z0-9_-]{1,64}") || orgId<1
                    || orgPath==null || !orgPath.matches("/[0-9/]{1,500}/")) throw new IllegalArgumentException("CENTRAL_APPROVAL_BRIDGE_INVALID");
        }
    }
    /** 默认record打印不能泄露传输凭据和身份映射。 */
    @Override public String toString() { return "CentralApprovalProperties[credentials=redacted]"; }
}
