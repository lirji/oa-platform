package com.lrj.oa.flow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.flow.infrastructure.mapper.FlowMappers;
import com.lrj.oa.flow.infrastructure.mapper.OutboxMapper;
import com.lrj.oa.flow.infrastructure.mapper.TodoMapper;
import com.lrj.oa.flow.infrastructure.workflow.LocalWorkflowGateway;
import com.lrj.oa.flow.infrastructure.workflow.WorkflowGateway;
import com.lrj.oa.iam.application.DelegationAuthorizationService;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ApprovalServiceDelegationTest {

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void forgedOnBehalfOfIsRejectedBeforeCompletingTask() {
        FlowMappers.ApprovalInstanceMapper instances = mock(FlowMappers.ApprovalInstanceMapper.class);
        TodoMapper todos = mock(TodoMapper.class);
        WorkflowGateway gateway = mock(WorkflowGateway.class);
        DelegationAuthorizationService delegations = mock(DelegationAuthorizationService.class);
        @SuppressWarnings("unchecked") ObjectProvider<LocalWorkflowGateway> local = mock(ObjectProvider.class);
        ApprovalService service = new ApprovalService(instances,
                mock(FlowMappers.FormTemplateMapper.class), mock(OutboxMapper.class), todos, gateway,
                local, new ObjectMapper(), delegations);

        when(gateway.findTasks(null, null)).thenReturn(List.of(new WorkflowGateway.Task(
                "task-1", "pi-1", "expense-v1", "B-1", "审批", "owner", null)));
        when(delegations.canActOnBehalf("attacker", "owner", "expense-v1", null)).thenReturn(false);
        UserContextHolder.set(new UserContext("attacker", "A", 1L, 10L, "/1/10/", 1L));

        assertThatThrownBy(() -> service.completeTask("task-1", "APPROVE", null, "owner"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.resultCode())
                                .isEqualTo(ResultCode.DELEGATION_INVALID));
        verify(gateway, never()).completeTask(anyString(), anyString(), anyString(), any(), any(), anyMap());
        verifyNoInteractions(todos);
    }
}
