package com.itheima.policydailyagent.service.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.itheima.policydailyagent.config.PolicySourceProperties;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskRound;
import com.itheima.policydailyagent.domain.search.SearchTaskRoundStatus;
import com.itheima.policydailyagent.domain.search.SearchTaskStatus;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.SearchTaskPolicyRepository;
import com.itheima.policydailyagent.repository.SearchTaskRoundRepository;
import com.itheima.policydailyagent.repository.SearchTaskSourceFailureRepository;
import com.itheima.policydailyagent.service.SearchTaskService;
import com.itheima.policydailyagent.service.memory.AgentTaskMemoryService;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryAssembler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchRoundServiceTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC);

    private final SearchTaskMemoryAssembler assembler = new SearchTaskMemoryAssembler(OBJECT_MAPPER);
    private final SearchRoundPlanner planner = new SearchRoundPlanner();
    private final TopicCoverageEvaluator coverageEvaluator = new TopicCoverageEvaluator();
    private final SearchStopPolicy stopPolicy = new SearchStopPolicy();

    @Test
    void runMultiRoundStopsWhenMaxRoundsReached() {
        SearchTask task = task();
        MultiRoundConfig config = config(true, 1);

        SearchRoundExecutor executor = mock(SearchRoundExecutor.class);
        when(executor.execute(eq(task), anyList(), anyList(), eq(5), eq(true), eq(1), any()))
                .thenReturn(new RoundExecution(1, 1, 0, 0, 0, 1, 1,
                        List.of("gov"), List.of(10L), List.of(), List.of()));

        SearchTaskRoundRepository roundRepository = roundRepositorySavingInPlace();
        SearchTaskPolicyRepository associationRepository = associationRepositoryReturningEmpty();
        PolicyDocumentRepository documentRepository = mock(PolicyDocumentRepository.class);
        SearchTaskService taskService = taskServiceCompleting(task);

        SearchRoundService service = service(taskService, mock(AgentTaskMemoryService.class),
                executor, roundRepository, associationRepository, documentRepository,
                mock(SearchTaskSourceFailureRepository.class), config);

        var result = service.runMultiRound(task, List.of("人工智能"), List.of("gov"),
                List.of(item()), 5, true);

        assertThat(result.roundCount()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(SearchTaskStatus.COMPLETED);
        verify(taskService).completeWithReason(eq(1L), anyString(), eq(1), eq(0), eq(0), eq(0), eq(0), eq("MAX_ROUNDS_REACHED"));

        ArgumentCaptor<SearchTaskRound> captor = ArgumentCaptor.forClass(SearchTaskRound.class);
        verify(roundRepository, atLeast(3)).save(captor.capture());
        assertThat(captor.getValue().getStopReason()).isEqualTo("MAX_ROUNDS_REACHED");
    }

    @Test
    void runMultiRoundStopsWithNoGrowthAfterTwoRounds() {
        SearchTask task = task();
        MultiRoundConfig config = config(true, 3);

        SearchRoundExecutor executor = mock(SearchRoundExecutor.class);
        when(executor.execute(eq(task), anyList(), anyList(), eq(5), eq(true), anyInt(), any()))
                .thenReturn(new RoundExecution(0, 0, 0, 0, 0, 0, 1,
                        List.of("gov"), List.of(), List.of(), List.of()));

        SearchTaskRoundRepository roundRepository = roundRepositorySavingInPlace();
        SearchTaskPolicyRepository associationRepository = associationRepositoryReturningEmpty();
        PolicyDocumentRepository documentRepository = mock(PolicyDocumentRepository.class);
        SearchTaskService taskService = taskServiceCompleting(task);

        SearchRoundService service = service(taskService, mock(AgentTaskMemoryService.class),
                executor, roundRepository, associationRepository, documentRepository,
                mock(SearchTaskSourceFailureRepository.class), config);

        var result = service.runMultiRound(task, List.of("人工智能"), List.of("gov"),
                List.of(item()), 5, true);

        assertThat(result.roundCount()).isEqualTo(2);
        verify(executor, times(2)).execute(eq(task), anyList(), anyList(), eq(5), eq(true), anyInt(), any());

        ArgumentCaptor<SearchTaskRound> captor = ArgumentCaptor.forClass(SearchTaskRound.class);
        verify(roundRepository, atLeast(4)).save(captor.capture());
        assertThat(captor.getValue().getStopReason()).isEqualTo("NO_GROWTH");
    }

    @Test
    void resumeContinuesFromNextRoundNumber() {
        SearchTask task = task();
        task.setStatus(SearchTaskStatus.RUNNING);
        MultiRoundConfig config = config(true, 3);
        task.setRunParamsJson(assembler.toJson(SearchRunParams.of(10, true, true, config)));

        SearchTaskRound round1 = new SearchTaskRound();
        round1.setSearchTaskId(1L);
        round1.setRoundNo(1);
        round1.setStatus(SearchTaskRoundStatus.COMPLETED);
        round1.setKeywordsJson("[\"人工智能\"]");
        round1.setTargetSourcesJson("[\"gov\"]");
        round1.setNewAssociationCount(1);
        round1.setFoundCount(1);

        SearchTaskRoundRepository roundRepository = mock(SearchTaskRoundRepository.class);
        when(roundRepository.findBySearchTaskIdOrderByRoundNoAsc(1L)).thenReturn(List.of(round1));
        when(roundRepository.save(any(SearchTaskRound.class))).thenAnswer(inv -> inv.getArgument(0));

        SearchRoundExecutor executor = mock(SearchRoundExecutor.class);
        when(executor.resolveSources(List.of("gov"))).thenReturn(List.of(item()));
        when(executor.execute(eq(task), eq(List.of("算力")), anyList(), eq(10), eq(true), eq(2), any()))
                .thenReturn(new RoundExecution(1, 1, 0, 0, 0, 1, 1,
                        List.of("gov"), List.of(11L), List.of(), List.of()));

        SearchTaskPolicyRepository associationRepository = associationRepositoryReturningEmpty();
        PolicyDocumentRepository documentRepository = mock(PolicyDocumentRepository.class);
        SearchTaskSourceFailureRepository sourceFailureRepository =
                mock(SearchTaskSourceFailureRepository.class);
        when(sourceFailureRepository.findBySearchTaskIdOrderByFirstRoundNoAsc(1L)).thenReturn(List.of());

        SearchTaskService taskService = mock(SearchTaskService.class);
        when(taskService.getTask(1L)).thenReturn(task);
        when(taskService.claimExecutor(eq(1L), anyString(), any(), any())).thenReturn(true);
        when(taskService.renewLease(eq(1L), anyString(), any())).thenReturn(true);
        SearchTask finished = new SearchTask();
        finished.setId(1L);
        finished.setStatus(SearchTaskStatus.COMPLETED);
        when(taskService.completeWithReason(eq(1L), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString()))
                .thenReturn(finished);
        stubOwnership(taskService);

        SearchRoundService service = service(taskService, mock(AgentTaskMemoryService.class),
                executor, roundRepository, associationRepository, documentRepository,
                sourceFailureRepository, config);

        var result = service.resume(1L);

        assertThat(result.roundCount()).isEqualTo(2);
        assertThat(result.foundCount()).isEqualTo(2);
        verify(executor).execute(eq(task), eq(List.of("算力")), anyList(), eq(10), eq(true), eq(2), any());
        verify(taskService).claimExecutor(eq(1L), anyString(), any(), any());
    }

    @Test
    void resumeThrowsWhenMultiRoundDisabled() {
        SearchRoundService service = service(mock(SearchTaskService.class),
                mock(AgentTaskMemoryService.class), mock(SearchRoundExecutor.class),
                mock(SearchTaskRoundRepository.class), mock(SearchTaskPolicyRepository.class),
                mock(PolicyDocumentRepository.class), mock(SearchTaskSourceFailureRepository.class),
                MultiRoundConfig.disabled());

        assertThatThrownBy(() -> service.resume(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void resumeRejectsActiveRunningTask() {
        SearchTask task = task();
        task.setStatus(SearchTaskStatus.RUNNING);
        task.setExecutorId("other-executor");
        task.setLeaseExpiresAt(java.time.LocalDateTime.now(CLOCK).plusHours(1));

        SearchTaskService taskService = mock(SearchTaskService.class);
        when(taskService.getTask(1L)).thenReturn(task);

        SearchRoundService service = service(taskService, mock(AgentTaskMemoryService.class),
                mock(SearchRoundExecutor.class), mock(SearchTaskRoundRepository.class),
                mock(SearchTaskPolicyRepository.class), mock(PolicyDocumentRepository.class),
                mock(SearchTaskSourceFailureRepository.class), config(true, 3));

        assertThatThrownBy(() -> service.resume(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("执行中");
    }

    @Test
    void resumeRejectsCompletedTask() {
        SearchTask task = task();
        task.setStatus(SearchTaskStatus.COMPLETED);

        SearchTaskService taskService = mock(SearchTaskService.class);
        when(taskService.getTask(1L)).thenReturn(task);

        SearchRoundService service = service(taskService, mock(AgentTaskMemoryService.class),
                mock(SearchRoundExecutor.class), mock(SearchTaskRoundRepository.class),
                mock(SearchTaskPolicyRepository.class), mock(PolicyDocumentRepository.class),
                mock(SearchTaskSourceFailureRepository.class), config(true, 3));

        assertThatThrownBy(() -> service.resume(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已正常完成");
    }

    @Test
    void resumeRejectsFailedTask() {
        SearchTask task = task();
        task.setStatus(SearchTaskStatus.FAILED);

        SearchTaskService taskService = mock(SearchTaskService.class);
        when(taskService.getTask(1L)).thenReturn(task);

        SearchRoundService service = service(taskService, mock(AgentTaskMemoryService.class),
                mock(SearchRoundExecutor.class), mock(SearchTaskRoundRepository.class),
                mock(SearchTaskPolicyRepository.class), mock(PolicyDocumentRepository.class),
                mock(SearchTaskSourceFailureRepository.class), config(true, 3));

        assertThatThrownBy(() -> service.resume(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已失败");
    }

    @Test
    void resumeThrowsWhenNoRoundRecords() {
        SearchTask task = task();
        task.setStatus(SearchTaskStatus.RUNNING);
        task.setRunParamsJson(assembler.toJson(SearchRunParams.of(10, true, true, config(true, 3))));
        SearchTaskService taskService = mock(SearchTaskService.class);
        when(taskService.getTask(1L)).thenReturn(task);
        SearchTaskRoundRepository roundRepository = mock(SearchTaskRoundRepository.class);
        when(roundRepository.findBySearchTaskIdOrderByRoundNoAsc(1L)).thenReturn(List.of());

        SearchRoundService service = service(taskService, mock(AgentTaskMemoryService.class),
                mock(SearchRoundExecutor.class), roundRepository, mock(SearchTaskPolicyRepository.class),
                mock(PolicyDocumentRepository.class), mock(SearchTaskSourceFailureRepository.class),
                config(true, 3));

        assertThatThrownBy(() -> service.resume(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("轮次记录");
    }

    @Test
    void resumeRejectsWhenParamsSnapshotMissing() {
        SearchTask task = task();
        task.setStatus(SearchTaskStatus.RUNNING);
        SearchTaskService taskService = mock(SearchTaskService.class);
        when(taskService.getTask(1L)).thenReturn(task);
        SearchTaskRoundRepository roundRepository = mock(SearchTaskRoundRepository.class);
        when(roundRepository.findBySearchTaskIdOrderByRoundNoAsc(1L)).thenReturn(List.of(
                completedRound(1)));

        SearchRoundService service = service(taskService, mock(AgentTaskMemoryService.class),
                mock(SearchRoundExecutor.class), roundRepository, mock(SearchTaskPolicyRepository.class),
                mock(PolicyDocumentRepository.class), mock(SearchTaskSourceFailureRepository.class),
                config(true, 3));

        assertThatThrownBy(() -> service.resume(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("参数快照");
    }

    @Test
    void runMultiRoundFailsTaskWhenExecuteThrows() {
        SearchTask task = task();
        MultiRoundConfig config = config(true, 1);

        SearchRoundExecutor executor = mock(SearchRoundExecutor.class);
        when(executor.execute(eq(task), anyList(), anyList(), eq(5), eq(true), eq(1), any()))
                .thenThrow(new RuntimeException("boom"));

        SearchTaskRoundRepository roundRepository = roundRepositorySavingInPlace();
        SearchTaskService taskService = taskServiceFailing(task);

        SearchRoundService service = service(taskService, mock(AgentTaskMemoryService.class),
                executor, roundRepository, associationRepositoryReturningEmpty(),
                mock(PolicyDocumentRepository.class), mock(SearchTaskSourceFailureRepository.class), config);

        var result = service.runMultiRound(task, List.of("人工智能"), List.of("gov"), List.of(item()), 5, true);

        assertThat(result.status()).isEqualTo(SearchTaskStatus.FAILED);
        verify(taskService).failWithCounts(eq(1L), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString());
        verify(taskService, never()).completeWithReason(anyLong(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString());
    }

    @Test
    void runMultiRoundFailsTaskWhenPlannedSaveThrows() {
        SearchTask task = task();
        MultiRoundConfig config = config(true, 1);

        SearchRoundExecutor executor = mock(SearchRoundExecutor.class);
        when(executor.execute(eq(task), anyList(), anyList(), eq(5), eq(true), eq(1), any()))
                .thenReturn(new RoundExecution(0, 0, 0, 0, 0, 0, 0,
                        List.of("gov"), List.of(), List.of(), List.of()));

        SearchTaskRoundRepository roundRepository = mock(SearchTaskRoundRepository.class);
        when(roundRepository.save(any(SearchTaskRound.class)))
                .thenThrow(new RuntimeException("db down"));

        SearchTaskService taskService = taskServiceFailing(task);

        SearchRoundService service = service(taskService, mock(AgentTaskMemoryService.class),
                executor, roundRepository, associationRepositoryReturningEmpty(),
                mock(PolicyDocumentRepository.class), mock(SearchTaskSourceFailureRepository.class), config);

        var result = service.runMultiRound(task, List.of("人工智能"), List.of("gov"), List.of(item()), 5, true);

        assertThat(result.status()).isEqualTo(SearchTaskStatus.FAILED);
        verify(taskService).failWithCounts(eq(1L), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void runMultiRoundAbortsWhenLeaseRenewalLost() {
        SearchTask task = task();
        MultiRoundConfig config = config(true, 1);

        SearchRoundExecutor executor = mock(SearchRoundExecutor.class);
        SearchTaskRoundRepository roundRepository = roundRepositorySavingInPlace();
        SearchTaskService taskService = mock(SearchTaskService.class);
        when(taskService.renewLease(eq(1L), anyString(), any())).thenReturn(false);

        SearchRoundService service = service(taskService, mock(AgentTaskMemoryService.class),
                executor, roundRepository, associationRepositoryReturningEmpty(),
                mock(PolicyDocumentRepository.class), mock(SearchTaskSourceFailureRepository.class), config);

        assertThatThrownBy(() -> service.runMultiRound(task, List.of("人工智能"), List.of("gov"), List.of(item()), 5, true))
                .isInstanceOf(LeaseLostException.class);
        verify(taskService, never()).completeWithReason(anyLong(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString());
        verify(taskService, never()).failWithCounts(anyLong(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString());
    }

    private SearchTaskService taskServiceFailing(SearchTask task) {
        SearchTaskService service = mock(SearchTaskService.class);
        SearchTask failed = new SearchTask();
        failed.setId(task.getId());
        failed.setStatus(SearchTaskStatus.FAILED);
        when(service.renewLease(eq(1L), anyString(), any())).thenReturn(true);
        when(service.failWithCounts(eq(1L), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(failed);
        stubOwnership(service);
        return service;
    }

    /**
     * 让 {@link SearchTaskService#withOwnership} 在单测里真实执行写入 lambda，从而走通
     * 「校验执行权 + 写入」的路径（校验通过即写入，不抛 LeaseLostException）。
     */
    private void stubOwnership(SearchTaskService taskService) {
        when(taskService.withOwnership(anyLong(), anyString(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());
    }

    private SearchTaskRound completedRound(int roundNo) {
        SearchTaskRound round = new SearchTaskRound();
        round.setSearchTaskId(1L);
        round.setRoundNo(roundNo);
        round.setStatus(SearchTaskRoundStatus.COMPLETED);
        round.setKeywordsJson("[\"人工智能\"]");
        round.setTargetSourcesJson("[\"gov\"]");
        round.setNewAssociationCount(1);
        return round;
    }

    private SearchRoundService service(
            SearchTaskService taskService,
            AgentTaskMemoryService memoryService,
            SearchRoundExecutor executor,
            SearchTaskRoundRepository roundRepository,
            SearchTaskPolicyRepository associationRepository,
            PolicyDocumentRepository documentRepository,
            SearchTaskSourceFailureRepository sourceFailureRepository,
            MultiRoundConfig config
    ) {
        return new SearchRoundService(taskService, memoryService, assembler, executor, roundRepository,
                associationRepository, documentRepository, sourceFailureRepository, planner,
                coverageEvaluator, stopPolicy, config, CLOCK);
    }

    private MultiRoundConfig config(boolean enabled, int maxRounds) {
        return new MultiRoundConfig(enabled, maxRounds, 2, 1, 5, 2000, List.of("人工智能", "算力"));
    }

    private SearchTaskRoundRepository roundRepositorySavingInPlace() {
        SearchTaskRoundRepository repo = mock(SearchTaskRoundRepository.class);
        when(repo.save(any(SearchTaskRound.class))).thenAnswer(inv -> inv.getArgument(0));
        return repo;
    }

    private SearchTaskPolicyRepository associationRepositoryReturningEmpty() {
        SearchTaskPolicyRepository repo = mock(SearchTaskPolicyRepository.class);
        when(repo.findBySearchTaskIdOrderByDiscoveryOrderAscIdAsc(1L)).thenReturn(List.of());
        return repo;
    }

    private SearchTaskService taskServiceCompleting(SearchTask task) {
        SearchTaskService service = mock(SearchTaskService.class);
        SearchTask finished = new SearchTask();
        finished.setId(task.getId());
        finished.setStatus(SearchTaskStatus.COMPLETED);
        when(service.renewLease(eq(1L), anyString(), any())).thenReturn(true);
        when(service.completeWithReason(eq(1L), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString()))
                .thenReturn(finished);
        stubOwnership(service);
        return service;
    }

    private PolicySourceProperties.Item item() {
        PolicySourceProperties.Item item = new PolicySourceProperties.Item();
        item.setId("gov");
        item.setName("中国政府网");
        item.setUrl("https://www.gov.cn/zhengce/zuixin/");
        return item;
    }

    private SearchTask task() {
        SearchTask task = new SearchTask();
        task.setId(1L);
        task.setTaskName("八月检索");
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能");
        task.setSourceIds("gov");
        return task;
    }
}
