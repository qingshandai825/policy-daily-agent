package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.agent.entity.AgentRun;
import com.itheima.policydailyagent.agent.repository.AgentRunRepository;
import com.itheima.policydailyagent.agent.repository.AgentStepRepository;
import com.itheima.policydailyagent.dto.PolicyDiscoverRequest;
import com.itheima.policydailyagent.entity.DailyTask;
import com.itheima.policydailyagent.repository.DailyTaskRepository;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyTaskServiceTests {

    private final DailyTaskRepository taskRepository = mock(DailyTaskRepository.class);
    private final PolicyDocumentRepository policyRepository = mock(PolicyDocumentRepository.class);
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final AgentStepRepository stepRepository = mock(AgentStepRepository.class);
    private final DailyTaskService service = new DailyTaskService(
            taskRepository,
            policyRepository,
            runRepository,
            stepRepository
    );

    @Test
    void shouldReuseSelectedWorkspaceAndKeepDateRangeOpen() {
        DailyTask task = mock(DailyTask.class);
        PolicyDiscoverRequest request = new PolicyDiscoverRequest(
                "https://example.gov.cn/list",
                List.of("人工智能"),
                20,
                false,
                null,
                null,
                "政策素材工作区",
                9L
        );
        when(taskRepository.findById(9L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        assertThat(service.startOrReuse(request, List.of("人工智能"))).isSameAs(task);

        verify(task).setTargetStartDate(null);
        verify(task).setTargetEndDate(null);
        verify(task).setSourceUrl("https://example.gov.cn/list");
        verify(task).markRunning();
    }

    @Test
    void shouldDeletePoliciesAndAgentHistoryWithWorkspace() {
        DailyTask task = mock(DailyTask.class);
        AgentRun run = mock(AgentRun.class);
        when(run.getId()).thenReturn(88L);
        when(taskRepository.findById(9L)).thenReturn(Optional.of(task));
        when(runRepository.findAllByDailyTaskId(9L)).thenReturn(List.of(run));

        service.deleteTask(9L);

        verify(stepRepository).deleteByDailyTaskId(9L);
        verify(stepRepository).deleteByAgentRunIdIn(List.of(88L));
        verify(runRepository).deleteByDailyTaskId(9L);
        verify(policyRepository).deleteByDailyTaskId(9L);
        verify(taskRepository).delete(task);
    }
}
