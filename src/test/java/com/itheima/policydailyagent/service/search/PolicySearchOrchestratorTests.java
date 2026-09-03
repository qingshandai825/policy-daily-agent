package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.config.PolicySourceProperties;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskStatus;
import com.itheima.policydailyagent.dto.SearchTaskRunRequest;
import com.itheima.policydailyagent.service.SearchTaskService;
import com.itheima.policydailyagent.service.memory.AgentTaskMemoryService;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PolicySearchOrchestratorTests {

    @Test
    void shouldRunSingleRoundWhenMultiRoundDisabled() {
        PolicySourceProperties.Item gov = source("gov", "中国政府网", "https://www.gov.cn/zhengce/zuixin/");
        PolicySourceProperties.Item miit = source("miit", "工业和信息化部", "https://www.miit.gov.cn/zwgk/zcwj/");

        SearchTaskService taskService = mock(SearchTaskService.class);
        SearchTask running = new SearchTask();
        running.setId(1L);
        running.setStatus(SearchTaskStatus.RUNNING);
        when(taskService.createAndStart(any(SearchTaskRunRequest.class), anyList(), anyList()))
                .thenReturn(running);
        SearchTask completed = new SearchTask();
        completed.setId(1L);
        completed.setStatus(SearchTaskStatus.COMPLETED);
        when(taskService.complete(eq(1L), eq(2), eq(2), eq(0), eq(0), eq(0)))
                .thenReturn(completed);

        AgentTaskMemoryService memoryService = mock(AgentTaskMemoryService.class);

        SearchRoundExecutor roundExecutor = mock(SearchRoundExecutor.class);
        when(roundExecutor.resolveSources(List.of("gov", "miit"))).thenReturn(List.of(gov, miit));
        when(roundExecutor.execute(eq(running), eq(List.of("人工智能")), anyList(), eq(5), eq(true), eq(1), any()))
                .thenReturn(new RoundExecution(2, 2, 0, 0, 0, 2, 2,
                        List.of("gov", "miit"), List.of(10L, 11L), List.of(), List.of()));

        SearchRoundService roundService = mock(SearchRoundService.class);
        MultiRoundConfig config = MultiRoundConfig.disabled();

        PolicySearchOrchestrator orchestrator = new PolicySearchOrchestrator(
                taskService, memoryService, roundExecutor, roundService, config);
        var result = orchestrator.run(new SearchTaskRunRequest(
                "八月检索",
                "2026-08",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of("人工智能"),
                List.of("gov", "miit"),
                5,
                true,
                null
        ));

        assertThat(result.status()).isEqualTo(SearchTaskStatus.COMPLETED);
        assertThat(result.foundCount()).isEqualTo(2);
        assertThat(result.associatedCount()).isEqualTo(2);
        assertThat(result.roundCount()).isEqualTo(1);
        verify(roundExecutor, times(1)).execute(eq(running), eq(List.of("人工智能")), anyList(), eq(5), eq(true), eq(1), any());
        verify(roundService, never()).runMultiRound(any(), anyList(), anyList(), anyList(), anyInt(), anyBoolean());

        verify(memoryService).initialize(running);
        verify(memoryService).markStarted(1L);
        verify(memoryService).recordRoundCompleted(eq(1L), any());
        verify(memoryService).markCompleted(eq(1L), any());
    }

    @Test
    void memoryRecordingFailureDoesNotBreakSearchCompletion() {
        PolicySourceProperties.Item gov = source("gov", "中国政府网", "https://www.gov.cn/zhengce/zuixin/");
        SearchTaskService taskService = mock(SearchTaskService.class);
        SearchTask running = new SearchTask();
        running.setId(1L);
        running.setStatus(SearchTaskStatus.RUNNING);
        when(taskService.createAndStart(any(SearchTaskRunRequest.class), anyList(), anyList()))
                .thenReturn(running);
        SearchTask completed = new SearchTask();
        completed.setId(1L);
        completed.setStatus(SearchTaskStatus.COMPLETED);
        when(taskService.complete(eq(1L), eq(1), eq(1), eq(0), eq(0), eq(0)))
                .thenReturn(completed);

        AgentTaskMemoryService memoryService = mock(AgentTaskMemoryService.class);
        doThrow(new RuntimeException("内存写入失败"))
                .when(memoryService).markCompleted(eq(1L), any());

        SearchRoundExecutor roundExecutor = mock(SearchRoundExecutor.class);
        when(roundExecutor.resolveSources(List.of("gov"))).thenReturn(List.of(gov));
        when(roundExecutor.execute(eq(running), eq(List.of("人工智能")), anyList(), eq(5), eq(true), eq(1), any()))
                .thenReturn(new RoundExecution(1, 1, 0, 0, 0, 1, 1,
                        List.of("gov"), List.of(10L), List.of(), List.of()));

        SearchRoundService roundService = mock(SearchRoundService.class);
        PolicySearchOrchestrator orchestrator = new PolicySearchOrchestrator(
                taskService, memoryService, roundExecutor, roundService, MultiRoundConfig.disabled());
        var result = orchestrator.run(new SearchTaskRunRequest(
                "八月检索", "2026-08",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                List.of("人工智能"), List.of("gov"), 5, true, null));

        assertThat(result.status()).isEqualTo(SearchTaskStatus.COMPLETED);
        assertThat(result.savedCount()).isEqualTo(1);
        verify(taskService).complete(eq(1L), eq(1), eq(1), eq(0), eq(0), eq(0));
    }

    @Test
    void delegatesToMultiRoundServiceWhenEnabledAndRequested() {
        PolicySourceProperties.Item gov = source("gov", "中国政府网", "https://www.gov.cn/zhengce/zuixin/");
        SearchTaskService taskService = mock(SearchTaskService.class);
        SearchTask running = new SearchTask();
        running.setId(1L);
        running.setStatus(SearchTaskStatus.RUNNING);
        when(taskService.createAndStart(any(SearchTaskRunRequest.class), anyList(), anyList()))
                .thenReturn(running);

        AgentTaskMemoryService memoryService = mock(AgentTaskMemoryService.class);
        SearchRoundExecutor roundExecutor = mock(SearchRoundExecutor.class);
        when(roundExecutor.resolveSources(List.of("gov"))).thenReturn(List.of(gov));

        SearchRoundService roundService = mock(SearchRoundService.class);
        SearchTask finished = new SearchTask();
        finished.setId(1L);
        finished.setStatus(SearchTaskStatus.COMPLETED);
        when(roundService.runMultiRound(eq(running), eq(List.of("人工智能")), eq(List.of("gov")),
                anyList(), eq(5), eq(true)))
                .thenReturn(new com.itheima.policydailyagent.dto.SearchTaskRunResult(
                        1L, SearchTaskStatus.COMPLETED, 0, 0, 0, 0, 0, 0, 2, List.of(), false));

        MultiRoundConfig config = new MultiRoundConfig(true, 3, 2, 1, 5, 2000,
                SearchTopicDictionary.DEFAULT_TOPICS);
        PolicySearchOrchestrator orchestrator = new PolicySearchOrchestrator(
                taskService, memoryService, roundExecutor, roundService, config);
        var result = orchestrator.run(new SearchTaskRunRequest(
                "八月检索", "2026-08",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                List.of("人工智能"), List.of("gov"), 5, true, true));

        assertThat(result.roundCount()).isEqualTo(2);
        verify(roundService).runMultiRound(eq(running), eq(List.of("人工智能")), eq(List.of("gov")),
                anyList(), eq(5), eq(true));
        verify(roundExecutor, never()).execute(any(), anyList(), anyList(), anyInt(), anyBoolean(), anyInt(), any());
    }

    private PolicySourceProperties.Item source(String id, String name, String url) {
        PolicySourceProperties.Item item = new PolicySourceProperties.Item();
        item.setId(id);
        item.setName(name);
        item.setUrl(url);
        return item;
    }
}
