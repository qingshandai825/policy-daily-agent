package com.itheima.policydailyagent.service.memory;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 任务级 Agent Memory 服务。第一阶段仅支持 POLICY_SEARCH 搜索任务。
 *
 * <p>关键约束：Memory 只记录摘要、业务对象 ID 与进度快照，绝不写完整政策正文、
 * 附件或 Word 文件；也绝不触碰政策审核状态（不把政策置为 ACCEPTED）。每个方法
 * 独立事务，调用方（搜索编排器）将 Memory 写入包装为尽力而为，从而保证 Memory
 * 记录失败不会导致已成功抓取的候选政策回滚。
 */
@Service
public class AgentTaskMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskMemoryService.class);

    private final AgentTaskMemoryRepository memoryRepository;
    private final AgentTaskEventRepository eventRepository;
    private final SearchTaskMemoryAssembler assembler;
    private final SearchTaskRepository searchTaskRepository;

    public AgentTaskMemoryService(
            AgentTaskMemoryRepository memoryRepository,
            AgentTaskEventRepository eventRepository,
            SearchTaskMemoryAssembler assembler,
            SearchTaskRepository searchTaskRepository
    ) {
        this.memoryRepository = memoryRepository;
        this.eventRepository = eventRepository;
        this.assembler = assembler;
        this.searchTaskRepository = searchTaskRepository;
    }

    /**
     * 创建搜索任务后初始化对应 Memory（幂等：已存在则直接返回，不重复创建）。
     */
    @Transactional
    public AgentTaskMemory initialize(SearchTask task) {
        return memoryRepository.findByTaskTypeAndBusinessTaskId(AgentTaskType.POLICY_SEARCH, task.getId())
                .orElseGet(() -> {
                    AgentTaskMemory memory = new AgentTaskMemory();
                    memory.setTaskType(AgentTaskType.POLICY_SEARCH);
                    memory.setBusinessTaskId(task.getId());
                    memory.setGoal(goalOf(task));
                    memory.setCurrentPhase(AgentTaskPhase.INITIALIZED);
                    memory.setMemoryStatus(AgentTaskMemoryStatus.ACTIVE);
                    memory.setSummary("搜索任务已创建，等待开始");
                    memory.setAutoRecovered(false);
                    memory.setContextJson(assembler.toJson(initialContext(task)));
                    memory.setNextAction("开始执行固定信源搜索");
                    return memoryRepository.save(memory);
                });
    }

    @Transactional
    public AgentTaskMemory markStarted(Long taskId) {
        AgentTaskMemory memory = require(taskId);
        updateSnapshot(memory, AgentTaskPhase.SEARCHING, "正在执行固定信源搜索",
                memory.getContextJson(), "逐个信源采集并入库候选政策");
        appendEvent(memory, 1, AgentTaskEventType.TASK_STARTED, null, null, "开始执行固定信源搜索");
        return memory;
    }

    @Transactional
    public AgentTaskMemory recordSourceSearched(
            Long taskId,
            int roundNo,
            String sourceId,
            String sourceName,
            SearchTaskMemoryContext context
    ) {
        AgentTaskMemory memory = require(taskId);
        String contextJson = assembler.toJson(context);
        updateSnapshot(memory, AgentTaskPhase.SEARCHING,
                "正在执行固定信源搜索（已完成 " + context.executedSources().size() + " 个信源）",
                contextJson, "继续执行剩余固定信源");
        appendEvent(memory, roundNo, AgentTaskEventType.SOURCE_SEARCHED,
                assembler.toJson(new SourceSearchInput(sourceId, sourceName, roundNo)),
                null, "信源采集成功：" + sourceName);
        return memory;
    }

    @Transactional
    public AgentTaskMemory recordSourceFailed(
            Long taskId,
            int roundNo,
            String sourceId,
            String sourceName,
            SearchTaskMemoryContext context,
            String errorSummary
    ) {
        AgentTaskMemory memory = require(taskId);
        String contextJson = assembler.toJson(context);
        updateSnapshot(memory, AgentTaskPhase.SEARCHING,
                "信源 " + sourceName + " 采集失败", contextJson, "继续执行剩余固定信源");
        appendEvent(memory, roundNo, AgentTaskEventType.SOURCE_FAILED,
                assembler.toJson(new SourceSearchInput(sourceId, sourceName, roundNo)),
                assembler.toJson(Map.of("error", safe(errorSummary))),
                errorSummary);
        return memory;
    }

    @Transactional
    public AgentTaskMemory recordRoundCompleted(Long taskId, SearchTaskMemoryContext context) {
        return recordRoundCompleted(taskId, 1, context);
    }

    @Transactional
    public AgentTaskMemory recordRoundCompleted(Long taskId, int roundNo, SearchTaskMemoryContext context) {
        AgentTaskMemory memory = require(taskId);
        String contextJson = assembler.toJson(context);
        updateSnapshot(memory, AgentTaskPhase.SEARCHING,
                "第 " + roundNo + " 轮搜索完成：" + summarize(context), contextJson, "进入下一轮或人工审核");
        appendEvent(memory, roundNo, AgentTaskEventType.ROUND_COMPLETED,
                null, assembler.toJson(context.counts()), "第 " + roundNo + " 轮搜索完成");
        return memory;
    }

    @Transactional
    public AgentTaskMemory recordRoundPlanned(
            Long taskId,
            int roundNo,
            List<String> keywords,
            List<String> sourceIds
    ) {
        AgentTaskMemory memory = require(taskId);
        updateSnapshot(memory, AgentTaskPhase.SEARCHING,
                "第 " + roundNo + " 轮计划已生成（关键词 " + (keywords == null ? 0 : keywords.size()) + " 个）",
                memory.getContextJson(), "开始执行第 " + roundNo + " 轮搜索");
        appendEvent(memory, roundNo, AgentTaskEventType.ROUND_PLANNED,
                null,
                assembler.toJson(Map.of("keywords", keywords == null ? List.of() : keywords,
                        "sources", sourceIds == null ? List.of() : sourceIds)),
                "第 " + roundNo + " 轮计划：" + String.join("、", keywords == null ? List.of() : keywords));
        return memory;
    }

    @Transactional
    public AgentTaskMemory recordRoundStarted(Long taskId, int roundNo) {
        AgentTaskMemory memory = require(taskId);
        appendEvent(memory, roundNo, AgentTaskEventType.ROUND_STARTED,
                null, null, "开始执行第 " + roundNo + " 轮搜索");
        return memory;
    }

    @Transactional
    public AgentTaskMemory recordCoverageEvaluated(
            Long taskId,
            int roundNo,
            List<SearchTaskMemoryContext.TopicCoverage> coverage
    ) {
        AgentTaskMemory memory = require(taskId);
        updateSnapshot(memory, AgentTaskPhase.SEARCHING,
                "第 " + roundNo + " 轮主题覆盖度评估完成", memory.getContextJson(), "根据覆盖度决定是否继续下一轮");
        appendEvent(memory, roundNo, AgentTaskEventType.COVERAGE_EVALUATED,
                null,
                assembler.toJson(Map.of("topicCoverage", coverage == null ? List.of() : coverage)),
                summarizeCoverage(coverage));
        return memory;
    }

    @Transactional
    public AgentTaskMemory markCompleted(Long taskId, SearchTaskMemoryContext context) {
        AgentTaskMemory memory = require(taskId);
        memory.setMemoryStatus(AgentTaskMemoryStatus.COMPLETED);
        updateSnapshot(memory, AgentTaskPhase.COMPLETED,
                "搜索任务完成：" + summarize(context), assembler.toJson(context),
                "进入人工审核候选政策");
        appendEvent(memory, 1, AgentTaskEventType.TASK_COMPLETED,
                null, assembler.toJson(context.counts()), "搜索任务完成");
        return memory;
    }

    @Transactional
    public AgentTaskMemory markFailed(
            Long taskId,
            SearchTaskMemoryContext context,
            String failureMessage,
            String nextAction
    ) {
        AgentTaskMemory memory = require(taskId);
        memory.setMemoryStatus(AgentTaskMemoryStatus.FAILED);
        updateSnapshot(memory, AgentTaskPhase.FAILED,
                "搜索任务失败：" + safe(failureMessage), assembler.toJson(context),
                hasText(nextAction) ? nextAction : "检查固定信源可用性后重新运行搜索任务");
        appendEvent(memory, 1, AgentTaskEventType.TASK_FAILED,
                null, assembler.toJson(Map.of("error", safe(failureMessage))), failureMessage);
        return memory;
    }

    @Transactional
    public void deleteForSearchTask(Long taskId) {
        memoryRepository.findByTaskTypeAndBusinessTaskId(AgentTaskType.POLICY_SEARCH, taskId)
                .ifPresent(memory -> {
                    eventRepository.deleteByMemoryId(memory.getId());
                    memoryRepository.delete(memory);
                });
    }

    @Transactional
    public AgentTaskMemoryView memoryView(Long taskId) {
        return toView(requireOrRecover(taskId));
    }

    @Transactional
    public List<AgentTaskEventView> eventViews(Long taskId) {
        AgentTaskMemory memory = requireOrRecover(taskId);
        return eventRepository.findByMemoryIdOrderByIdAsc(memory.getId())
                .stream()
                .map(this::toEventView)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentTaskMemory requireForSearchTask(Long taskId) {
        return require(taskId);
    }

    public SearchTaskMemoryContext buildContext(
            SearchTask task,
            List<String> executedSources,
            List<Long> candidatePolicyIds,
            SearchTaskMemoryContext.Counts counts,
            List<SearchTaskMemoryContext.SourceFailure> sourceFailures
    ) {
        return assembler.contextOf(task, executedSources, candidatePolicyIds, counts, sourceFailures);
    }

    private SearchTaskMemoryContext initialContext(SearchTask task) {
        return assembler.contextOf(task, List.of(), List.of(),
                SearchTaskMemoryContext.Counts.zero(), List.of());
    }

    private AgentTaskMemory require(Long taskId) {
        return memoryRepository.findByTaskTypeAndBusinessTaskId(AgentTaskType.POLICY_SEARCH, taskId)
                .orElseThrow(() -> new IllegalArgumentException("搜索任务 Memory 不存在，taskId=" + taskId));
    }

    /**
     * 幂等补建：读取路径调用。若 Memory 缺失但搜索任务存在，说明初始化被中断，
     * 按明确规则补建一份最小快照并显著标记，绝不掩盖数据不一致。
     */
    private AgentTaskMemory requireOrRecover(Long taskId) {
        Optional<AgentTaskMemory> existing = memoryRepository
                .findByTaskTypeAndBusinessTaskId(AgentTaskType.POLICY_SEARCH, taskId);
        if (existing.isPresent()) {
            return existing.get();
        }
        SearchTask task = searchTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("搜索任务不存在，id=" + taskId));
        log.warn("搜索任务 {} 的 Memory 缺失，按幂等规则自动补建（可能由初始化中断导致）", taskId);
        AgentTaskMemory memory = new AgentTaskMemory();
        memory.setTaskType(AgentTaskType.POLICY_SEARCH);
        memory.setBusinessTaskId(task.getId());
        memory.setGoal(goalOf(task));
        memory.setCurrentPhase(AgentTaskPhase.INITIALIZED);
        memory.setMemoryStatus(AgentTaskMemoryStatus.ACTIVE);
        memory.setSummary("搜索任务 Memory 缺失，已自动补建（仅用于追溯，不代表搜索进度）");
        memory.setAutoRecovered(true);
        memory.setContextJson(assembler.toJson(initialContext(task)));
        memory.setNextAction("重新运行搜索任务以恢复进度");
        return memoryRepository.save(memory);
    }

    private AgentTaskMemory updateSnapshot(
            AgentTaskMemory memory,
            AgentTaskPhase phase,
            String summary,
            String contextJson,
            String nextAction
    ) {
        memory.setCurrentPhase(phase);
        memory.setSummary(summary);
        memory.setContextJson(contextJson);
        memory.setNextAction(nextAction);
        return memoryRepository.save(memory);
    }

    private void appendEvent(
            AgentTaskMemory memory,
            int roundNo,
            AgentTaskEventType type,
            String inputJson,
            String outputJson,
            String decision
    ) {
        AgentTaskEvent event = new AgentTaskEvent();
        event.setMemoryId(memory.getId());
        event.setRoundNo(roundNo);
        event.setEventType(type);
        event.setInputJson(inputJson);
        event.setOutputJson(outputJson);
        event.setDecision(decision);
        eventRepository.save(event);
    }

    private AgentTaskMemoryView toView(AgentTaskMemory memory) {
        return new AgentTaskMemoryView(
                memory.getId(),
                memory.getTaskType(),
                memory.getBusinessTaskId(),
                memory.getGoal(),
                memory.getCurrentPhase(),
                memory.getMemoryStatus(),
                memory.getSummary(),
                memory.isAutoRecovered(),
                assembler.parseContext(memory.getContextJson()),
                memory.getNextAction(),
                memory.getVersionNo(),
                memory.getCreatedAt(),
                memory.getUpdatedAt()
        );
    }

    private AgentTaskEventView toEventView(AgentTaskEvent event) {
        return new AgentTaskEventView(
                event.getId(),
                event.getMemoryId(),
                event.getRoundNo(),
                event.getEventType(),
                event.getInputJson(),
                event.getOutputJson(),
                event.getDecision(),
                event.getCreatedAt()
        );
    }

    private String goalOf(SearchTask task) {
        String month = hasText(task.getReportMonth()) ? task.getReportMonth() + " " : "";
        return "为" + month + "政策月报检索候选政策（"
                + task.getTargetStartDate() + " 至 " + task.getTargetEndDate() + "）";
    }

    private String summarize(SearchTaskMemoryContext context) {
        SearchTaskMemoryContext.Counts c = context.counts();
        return "发现 " + c.found() + "、保存 " + c.saved() + "、重复 " + c.duplicate()
                + "、过滤 " + c.filtered() + "、失败 " + c.failed();
    }

    private String summarizeCoverage(List<SearchTaskMemoryContext.TopicCoverage> coverage) {
        if (coverage == null || coverage.isEmpty()) {
            return "无主题覆盖度结果";
        }
        long covered = coverage.stream().filter(c -> "COVERED".equals(c.status())).count();
        return "主题覆盖度：已覆盖 " + covered + "/" + coverage.size();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
