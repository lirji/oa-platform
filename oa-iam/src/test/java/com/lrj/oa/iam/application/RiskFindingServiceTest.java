package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.RiskFindingDtos.ChangeStatusRequest;
import com.lrj.oa.iam.api.dto.RiskFindingDtos.ScanResult;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.RiskFinding;
import com.lrj.oa.iam.infrastructure.mapper.RiskFindingMapper;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class RiskFindingServiceTest {

    private final RiskFindingMapper findings = mock(RiskFindingMapper.class);
    private final RiskFindingService service = new RiskFindingService(findings);

    @BeforeEach
    void login() {
        UserContextHolder.set(new UserContext("admin", "admin", 1L, 2L, "/1/2/", 1L));
    }

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void scanOpensThreeBuiltinRules() {
        when(findings.selectStaleUnused(eq(1L), any())).thenReturn(List.of(identity("sa-1", "SERVICE_ACCOUNT")));
        when(findings.selectLeaverResidual(eq(1L), any())).thenReturn(List.of(identity("id-1", "USER")));
        when(findings.selectOrphanAgents(1L)).thenReturn(List.of(identity("ag-1", "AGENT")));
        when(findings.insertOpen(any())).thenReturn(1);

        ScanResult result = service.scan();

        assertThat(result.opened()).isEqualTo(3);
        assertThat(result.scannedRules()).isEqualTo(3);
        verify(findings).selectStaleUnused(eq(1L), any());
        verify(findings).selectLeaverResidual(eq(1L), any());
        verify(findings).selectOrphanAgents(1L);
    }

    @Test
    void scanDoesNotDuplicateActiveFinding() {
        when(findings.selectStaleUnused(eq(1L), any())).thenReturn(List.of(identity("sa-1", "SERVICE_ACCOUNT")));
        when(findings.selectLeaverResidual(eq(1L), any())).thenReturn(List.of());
        when(findings.selectOrphanAgents(1L)).thenReturn(List.of());
        when(findings.insertOpen(any())).thenReturn(0);

        ScanResult result = service.scan();

        assertThat(result.opened()).isEqualTo(0);
    }

    @Test
    void openToResolved() {
        RiskFinding row = finding(9L, "OPEN");
        when(findings.selectById(1L, 9L)).thenReturn(row, resolved(9L));
        when(findings.updateStatus(eq(1L), eq(9L), eq("OPEN"), eq("RESOLVED"),
                any(), eq("admin"), any())).thenReturn(1);

        assertThat(service.changeStatus(9L, new ChangeStatusRequest("RESOLVED", "已收权")).status())
                .isEqualTo("RESOLVED");
    }

    @Test
    void resolvedCannotMove() {
        when(findings.selectById(1L, 9L)).thenReturn(finding(9L, "RESOLVED"));

        assertThatThrownBy(() -> service.changeStatus(9L, new ChangeStatusRequest("OPEN", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(findings, never()).updateStatus(anyLong(), anyLong(), anyString(), anyString(),
                any(), anyString(), any());
    }

    @Test
    void missingFindingIsNotFound() {
        when(findings.selectById(1L, 9L)).thenReturn(null);
        assertThatThrownBy(() -> service.changeStatus(9L, new ChangeStatusRequest("RESOLVED", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void listUnknownStatusIsBadRequest() {
        assertThatThrownBy(() -> service.list("WEIRD", null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
    }

    private static Identity identity(String id, String type) {
        Identity row = new Identity();
        row.setId(id);
        row.setIdentityType(type);
        row.setDisplayName(id);
        row.setExternalKey(id);
        row.setStatus("ACTIVE");
        return row;
    }

    private static RiskFinding finding(long id, String status) {
        RiskFinding row = new RiskFinding();
        row.setId(id);
        row.setRuleCode("AGENT_ORPHAN");
        row.setSeverity("HIGH");
        row.setIdentityId("ag-1");
        row.setIdentityType("AGENT");
        row.setDisplayName("bot");
        row.setExternalKey("agent-1");
        row.setSummary("Agent 无有效属主");
        row.setStatus(status);
        return row;
    }

    private static RiskFinding resolved(long id) {
        RiskFinding row = finding(id, "RESOLVED");
        row.setUpdatedBy("admin");
        return row;
    }
}
