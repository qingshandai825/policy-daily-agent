package com.itheima.policydailyagent.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.agent.repository.AgentRunRepository;
import com.itheima.policydailyagent.agent.repository.AgentStepRepository;
import com.itheima.policydailyagent.repository.DailyTaskRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentMemoryServiceTests {

    private final AgentRunRepository agentRunRepository = mock(AgentRunRepository.class);
    private final AgentStepRepository agentStepRepository = mock(AgentStepRepository.class);
    private final DailyTaskRepository dailyTaskRepository = mock(DailyTaskRepository.class);
    private final AgentMemoryService memoryService = new AgentMemoryService(
            agentRunRepository,
            agentStepRepository,
            dailyTaskRepository,
            new ObjectMapper()
    );

    @Test
    void shouldRejectUnknownTaskBeforeCreatingAgentRun() {
        when(dailyTaskRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> memoryService.ensureRunForTask(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("404");

        verifyNoInteractions(agentRunRepository);
    }
}
