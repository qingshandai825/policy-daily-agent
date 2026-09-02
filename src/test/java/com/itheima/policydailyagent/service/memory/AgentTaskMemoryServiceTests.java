package com.itheima.policydailyagent.service.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.itheima.policydailyagent.domain.memory.AgentTaskEvent;
import com.itheima.policydailyagent.domain.memory.AgentTaskEventType;
import com.itheima.policydailyagent.domain.memory.AgentTaskMemory;
import com.itheima.policydailyagent.domain.memory.AgentTaskMemoryStatus;
import com.itheima.policydailyagent.domain.memory.AgentTaskPhase;
import com.itheima.policydailyagent.domain.memory.AgentTaskType;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.dto.memory.AgentTaskEventView;
import com.itheima.policydailyagent.dto.memory.AgentTaskMemoryView;
import com.itheima.policydailyagent.repository.AgentTaskEventRepository;
import com.itheima.policydailyagent.repository.AgentTaskMemoryRepository;
import com.itheima.policydailyagent.repository.SearchTaskRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentTaskMemoryServiceTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final AgentTaskMemoryRepository memoryRepository = mock(AgentTaskMemoryRepository.class);
    private final AgentTaskEventRepository eventRepository = mock(AgentTaskEventRepository.class);
    private final SearchTaskRepository searchTaskRepository = mock(SearchTaskRepository.class);
    private final SearchTaskMemoryAssembler assembler = new SearchTaskMemoryAssembler(OBJECT_MAPPER);
    private final AgentTaskMemoryService service =
            new AgentTaskMemoryService(memoryRepository, eventRepository, assembler, searchTaskRepository);

    @Test
    void initializeCreatesSingleMemoryAndIsIdempotent() {
        SearchTask task = task(7L);
        AgentTaskMemory existing = memory(9L);
        when(memoryRepository.findByTaskTypeAndBusinessTaskId(AgentTaskType.POLICY_SEARCH, 7L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(memoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AgentTaskMemory first = service.initialize(task);
        AgentTaskMemory second = service.initialize(task);

        assertThat(first.getTaskType()).isEqualTo(AgentTaskType.POLICY_SEARCH);
        assertThat(first.getBusinessTaskId()).isEqualTo(7L);
        assertThat(first.getCurrentPhase()).isEqualTo(AgentTaskPhase.INITIALIZED);
        assertThat(first.getMemoryStatus()).isEqualTo(AgentTaskMemoryStatus.ACTIVE);
        assertThat(first.getVersionNo()).isZero();
        // 一个 SearchTask 只能有一份当前 Memory：第二次初始化命中已存在记录，不再新建
        assertThat(second).isSameAs(existing);
        verify(memoryRepository, times(1)).save(any());
    }

    @Test
    void eventViewsAreReturnedInIdAscOrder() {
        AgentTaskMemory memory = memory(9L);
        when(memoryRepository.findByTaskTypeAndBusinessTaskId(AgentTaskType.POLICY_SEARCH, 7L))
                .thenReturn(Optional.of(memory));
        when(eventRepository.findByMemoryIdOrderByIdAsc(9L)).thenReturn(List.of(
                event(1L, AgentTaskEventType.TASK_STARTED),
                event(2L, AgentTaskEventType.SOURCE_SEARCHED),
                event(3L, AgentTaskEventType.TASK_COMPLETED)
        ));

        List<AgentTaskEventView> views = service.eventViews(7L);

        assertThat(views).extracting(AgentTaskEventView::id).containsExactly(1L, 2L, 3L);
        verify(eventRepository).findByMemoryIdOrderByIdAsc(9L);
    }

    @Test
    void memoryTransitionsFromStartedToCompletedWithEvents() {
        SearchTask task = task(7L);
        AgentTaskMemory memory = setupMemory(task);
        SearchTaskMemoryContext context = context(task);

        service.markStarted(7L);
        service.recordRoundCompleted(7L, context);
        service.markCompleted(7L, context);

        assertThat(memory.getCurrentPhase()).isEqualTo(AgentTaskPhase.COMPLETED);
        assertThat(memory.getMemoryStatus()).isEqualTo(AgentTaskMemoryStatus.COMPLETED);
        assertThat(memory.getSummary()).contains("发现 2");

        ArgumentCaptor<AgentTaskEvent> captor = ArgumentCaptor.forClass(AgentTaskEvent.class);
        verify(eventRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentTaskEvent::getEventType)
                .containsExactly(
                        AgentTaskEventType.TASK_STARTED,
                        AgentTaskEventType.ROUND_COMPLETED,
                        AgentTaskEventType.TASK_COMPLETED
                );
    }

    @Test
    void memoryTransitionsToFailedWithRecoverableNextAction() {
        SearchTask task = task(7L);
        AgentTaskMemory memory = setupMemory(task);

        service.markFailed(7L, context(task), "所有信源均采集失败", "检查固定信源可用性后重新运行搜索任务");

        assertThat(memory.getCurrentPhase()).isEqualTo(AgentTaskPhase.FAILED);
        assertThat(memory.getMemoryStatus()).isEqualTo(AgentTaskMemoryStatus.FAILED);
        assertThat(memory.getSummary()).contains("所有信源均采集失败");
        assertThat(memory.getNextAction()).isEqualTo("检查固定信源可用性后重新运行搜索任务");

        ArgumentCaptor<AgentTaskEvent> captor = ArgumentCaptor.forClass(AgentTaskEvent.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AgentTaskEventType.TASK_FAILED);
        assertThat(captor.getValue().getDecision()).contains("所有信源均采集失败");
    }

    @Test
    void sourceFailedEventRetainsErrorSummary() {
        SearchTask task = task(7L);
        AgentTaskMemory memory = setupMemory(task);
        SearchTaskMemoryContext context = assembler.contextOf(
                task,
                List.of("gov"),
                List.of(),
                SearchTaskMemoryContext.Counts.of(0, 0, 0, 0, 1),
                List.of(new SearchTaskMemoryContext.SourceFailure("miit", "工信部", "连接超时"))
        );

        service.recordSourceFailed(7L, 1, "miit", "工信部", context, "连接超时");

        ArgumentCaptor<AgentTaskEvent> captor = ArgumentCaptor.forClass(AgentTaskEvent.class);
        verify(eventRepository).save(captor.capture());
        AgentTaskEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AgentTaskEventType.SOURCE_FAILED);
        assertThat(event.getDecision()).isEqualTo("连接超时");
        assertThat(event.getInputJson()).contains("miit");
        assertThat(event.getOutputJson()).contains("连接超时");
        // 错误摘要同时进入 Memory 快照，便于追溯
        assertThat(memory.getContextJson()).contains("连接超时");
    }

    @Test
    void completingMemoryDoesNotTouchPolicyReviewStatus() {
        // Memory 只记录候选政策 ID，绝不写 policy_document / policy_review，
        // 更不会把政策置为 ACCEPTED。该服务不依赖任何审核组件，完整生命周期的
        // 唯一副作用是 memory / event 两个仓库。
        SearchTask task = task(7L);
        AgentTaskMemory memory = setupMemory(task);
        SearchTaskMemoryContext context = context(task);

        service.markStarted(7L);
        service.recordSourceSearched(7L, 1, "gov", "中国政府网", context);
        service.recordRoundCompleted(7L, context);
        service.markCompleted(7L, context);

        assertThat(memory.getMemoryStatus()).isEqualTo(AgentTaskMemoryStatus.COMPLETED);
        verify(memoryRepository, atLeastOnce())
                .findByTaskTypeAndBusinessTaskId(AgentTaskType.POLICY_SEARCH, 7L);
        verify(memoryRepository, atLeastOnce()).save(memory);
        verify(eventRepository, atLeastOnce()).save(any(AgentTaskEvent.class));
        verifyNoMoreInteractions(memoryRepository, eventRepository);
    }

    @Test
    void queryingMemoryForMissingTaskThrows() {
        when(memoryRepository.findByTaskTypeAndBusinessTaskId(AgentTaskType.POLICY_SEARCH, 999L))
                .thenReturn(Optional.empty());
        when(searchTaskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.memoryView(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("搜索任务不存在");
        assertThatThrownBy(() -> service.eventViews(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("搜索任务不存在");
    }

    @Test
    void queryingRecoversMissingMemoryWhenTaskExists() {
        SearchTask task = task(7L);
        when(memoryRepository.findByTaskTypeAndBusinessTaskId(AgentTaskType.POLICY_SEARCH, 7L))
                .thenReturn(Optional.empty());
        when(searchTaskRepository.findById(7L)).thenReturn(Optional.of(task));
        when(memoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AgentTaskMemoryView view = service.memoryView(7L);

        assertThat(view.businessTaskId()).isEqualTo(7L);
        assertThat(view.memoryStatus()).isEqualTo(AgentTaskMemoryStatus.ACTIVE);
        assertThat(view.currentPhase()).isEqualTo(AgentTaskPhase.INITIALIZED);
        assertThat(view.summary()).contains("自动补建");
        verify(memoryRepository, times(1)).save(any());
        verify(eventRepository, never()).save(any());
    }

    private AgentTaskMemory setupMemory(SearchTask task) {
        AgentTaskMemory memory = memory(9L);
        memory.setBusinessTaskId(task.getId());
        memory.setGoal("为2026-08 政策月报检索候选政策");
        memory.setContextJson(assembler.toJson(
                assembler.contextOf(task, List.of(), List.of(),
                        SearchTaskMemoryContext.Counts.zero(), List.of())));
        when(memoryRepository.findByTaskTypeAndBusinessTaskId(AgentTaskType.POLICY_SEARCH, task.getId()))
                .thenReturn(Optional.of(memory));
        when(memoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return memory;
    }

    private SearchTaskMemoryContext context(SearchTask task) {
        return assembler.contextOf(
                task,
                List.of("gov", "miit"),
                List.of(10L, 11L),
                SearchTaskMemoryContext.Counts.of(2, 1, 0, 0, 0),
                List.of()
        );
    }

    private SearchTask task(long id) {
        SearchTask task = new SearchTask();
        task.setId(id);
        task.setTaskName("八月检索");
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能,大模型");
        task.setSourceIds("gov,miit");
        return task;
    }

    private AgentTaskMemory memory(long id) {
        AgentTaskMemory memory = new AgentTaskMemory();
        memory.setId(id);
        memory.setTaskType(AgentTaskType.POLICY_SEARCH);
        memory.setCurrentPhase(AgentTaskPhase.INITIALIZED);
        memory.setMemoryStatus(AgentTaskMemoryStatus.ACTIVE);
        memory.setVersionNo(1);
        return memory;
    }

    private AgentTaskEvent event(long id, AgentTaskEventType type) {
        AgentTaskEvent event = new AgentTaskEvent();
        event.setId(id);
        event.setMemoryId(9L);
        event.setRoundNo(1);
        event.setEventType(type);
        return event;
    }
}
