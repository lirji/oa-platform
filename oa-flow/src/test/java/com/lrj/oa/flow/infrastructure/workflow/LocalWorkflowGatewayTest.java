package com.lrj.oa.flow.infrastructure.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalWorkflowGatewayTest {

    @Test
    void idsRemainUniqueAcrossApplicationRestarts() {
        LocalWorkflowGateway firstRun = new LocalWorkflowGateway();
        LocalWorkflowGateway secondRun = new LocalWorkflowGateway();

        String firstPid = firstRun.startProcess("approval", "BIZ-1", List.of("user-1"));
        String secondPid = secondRun.startProcess("approval", "BIZ-2", List.of("user-1"));
        String firstTask = firstRun.findTasks("approval", "BIZ-1").getFirst().taskId();
        String secondTask = secondRun.findTasks("approval", "BIZ-2").getFirst().taskId();

        assertThat(firstPid).startsWith("local-pi-").isNotEqualTo(secondPid);
        assertThat(firstTask).startsWith("local-task-").isNotEqualTo(secondTask);
    }
}
