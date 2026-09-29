package com.lrj.oa.flow.infrastructure.approval;

import com.lrj.authz.protocol.ApprovalSignature;
import com.lrj.oa.flow.infrastructure.mapper.CentralApprovalMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 独立服务认证链的伪造、重放与禁用负例；不使用员工管理员角色替代签名。 */
class CentralApprovalSecurityTest {
    private static final String KEY="k".repeat(43), PATH="/internal/iam-approval/v1/start";
    private CentralApprovalProperties config(boolean enabled) {
        return new CentralApprovalProperties(enabled,"a".repeat(36),"commerce","test",1,KEY,true,
                Map.of("member",new CentralApprovalProperties.Bridge(1,"user",null,1,"/1/")));
    }
    private MockHttpServletRequest request(byte[] body,String signature) {
        var request=new MockHttpServletRequest("POST",PATH);request.setContent(body);request.addHeader(ApprovalSignature.HEADER,signature);
        request.setRemoteAddr("127.0.0.1");request.setLocalAddr("127.0.0.1");return request;
    }
    @Test void changedBytesNeverReachControllerOrNonceStore() throws Exception {
        var mapper=mock(CentralApprovalMapper.class);var response=new MockHttpServletResponse();var invoked=new AtomicBoolean();
        String signature=ApprovalSignature.sign(KEY,"auth-platform","test",PATH,new byte[]{1},Instant.now());
        new CentralApprovalSecurity.SignedFilter(config(true),mapper).doFilter(request(new byte[]{2},signature),response,(r,s)->invoked.set(true));
        assertThat(response.getStatus()).isEqualTo(401);assertThat(invoked).isFalse();verifyNoInteractions(mapper);
    }
    @Test void acceptedNonceIsRegisteredAndReplayedTransportIsRejected() throws Exception {
        var mapper=mock(CentralApprovalMapper.class);when(mapper.nonce(anyString())).thenReturn(1,0);
        byte[] body="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String signature=ApprovalSignature.sign(KEY,"auth-platform","test",PATH,body,Instant.now());
        var filter=new CentralApprovalSecurity.SignedFilter(config(true),mapper);var first=new MockHttpServletResponse();var invoked=new AtomicBoolean();
        filter.doFilter(request(body,signature),first,(r,s)->{invoked.set(true);assertThat(r.getAttribute(CentralApprovalSecurity.BODY)).isEqualTo(body);});
        assertThat(invoked).isTrue();var second=new MockHttpServletResponse();
        filter.doFilter(request(body,signature),second,(r,s)->{throw new AssertionError("replayed transport");});
        assertThat(second.getStatus()).isEqualTo(409);
    }
    @Test void disabledNamespaceDoesNotFallThrough() throws Exception {
        var mapper=mock(CentralApprovalMapper.class);var response=new MockHttpServletResponse();
        new CentralApprovalSecurity.SignedFilter(config(false),mapper).doFilter(request(new byte[0],"invalid"),response,(r,s)->{throw new AssertionError("disabled");});
        assertThat(response.getStatus()).isEqualTo(404);verifyNoInteractions(mapper);
    }
}
