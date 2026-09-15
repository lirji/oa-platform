package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.CredentialDtos.CreateCredential;
import com.lrj.oa.iam.api.dto.CredentialDtos.IssuedCredential;
import com.lrj.oa.iam.domain.Credential;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.infrastructure.mapper.CredentialMapper;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class CredentialServiceTest {

    private final CredentialMapper mapper = mock(CredentialMapper.class);
    private final IdentityMapper identities = mock(IdentityMapper.class);
    private final CredentialService service = new CredentialService(mapper, identities);

    @BeforeEach
    void login() {
        UserContextHolder.set(new UserContext("admin", "admin", 1L, 2L, "/1/2/", 1L));
    }

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void issueReturnsPlaintextOnceAndStoresHash() {
        when(identities.selectById(1L, "sa-1")).thenReturn(serviceAccount());
        final Credential[] inserted = new Credential[1];
        when(mapper.insert(any())).thenAnswer(inv -> {
            Credential row = inv.getArgument(0);
            row.setId(3L);
            inserted[0] = row;
            return 1;
        });
        when(mapper.selectById(1L, 3L)).thenAnswer(inv -> inserted[0]);

        IssuedCredential issued = service.issue("sa-1", new CreateCredential("API_KEY", null));

        assertThat(issued.secret()).startsWith("oa_sk_");
        assertThat(issued.credential().last4()).isEqualTo(issued.secret().substring(issued.secret().length() - 4));
        assertThat(inserted[0].getSecretHash()).isEqualTo(CredentialService.sha256(issued.secret()));
        assertThat(inserted[0].getSecretHash()).isNotEqualTo(issued.secret());
    }

    @Test
    void humanIdentityCannotGetCredential() {
        Identity user = serviceAccount();
        user.setIdentityType("USER");
        when(identities.selectById(1L, "id-1")).thenReturn(user);

        assertThatThrownBy(() -> service.issue("id-1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(mapper, never()).insert(any());
    }

    @Test
    void listDoesNotExposeHash() {
        when(identities.selectById(1L, "sa-1")).thenReturn(serviceAccount());
        when(mapper.selectByIdentity(1L, "sa-1")).thenReturn(List.of(storedActive(3L)));

        var views = service.list("sa-1");

        assertThat(views).hasSize(1);
        assertThat(views.get(0).last4()).isEqualTo("abcd");
        assertThat(views.get(0).toString()).doesNotContain("secretHash");
    }

    @Test
    void revokeMarksInactive() {
        when(mapper.selectById(1L, 3L)).thenReturn(storedActive(3L), revoked(3L));
        when(identities.selectById(1L, "sa-1")).thenReturn(serviceAccount());
        when(mapper.markInactive(eq(1L), eq(3L), eq("REVOKED"), eq("admin"), any())).thenReturn(1);

        assertThat(service.revoke(3L).status()).isEqualTo("REVOKED");
    }

    @Test
    void rotateIssuesNewAndMarksOldRotated() {
        when(mapper.selectById(eq(1L), eq(3L))).thenReturn(storedActive(3L));
        when(identities.selectById(1L, "sa-1")).thenReturn(serviceAccount());
        when(mapper.markInactive(eq(1L), eq(3L), eq("ROTATED"), eq("admin"), any())).thenReturn(1);
        when(mapper.insert(any())).thenAnswer(inv -> {
            Credential row = inv.getArgument(0);
            row.setId(4L);
            return 1;
        });
        when(mapper.selectById(1L, 4L)).thenReturn(storedActive(4L));

        IssuedCredential rotated = service.rotate(3L);

        assertThat(rotated.secret()).isNotBlank();
        assertThat(rotated.credential().id()).isEqualTo(4L);
        verify(mapper).markInactive(eq(1L), eq(3L), eq("ROTATED"), eq("admin"), any());
    }

    private static Identity serviceAccount() {
        Identity row = new Identity();
        row.setId("sa-1");
        row.setTenantId(1L);
        row.setIdentityType("SERVICE_ACCOUNT");
        row.setStatus("ACTIVE");
        row.setExternalKey("sa-report");
        row.setDisplayName("报表机器人");
        return row;
    }

    private static Credential storedActive(long id) {
        Credential row = new Credential();
        row.setId(id);
        row.setTenantId(1L);
        row.setIdentityId("sa-1");
        row.setKind("API_KEY");
        row.setSecretHash("hash");
        row.setLast4("abcd");
        row.setStatus("ACTIVE");
        row.setCreatedBy("admin");
        return row;
    }

    private static Credential revoked(long id) {
        Credential row = storedActive(id);
        row.setStatus("REVOKED");
        return row;
    }
}
