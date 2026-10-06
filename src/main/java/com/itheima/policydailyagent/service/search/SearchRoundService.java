package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.config.PolicySourceProperties;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskPolicy;
import com.itheima.policydailyagent.domain.search.SearchTaskRound;
import com.itheima.policydailyagent.domain.search.SearchTaskRoundStatus;
import com.itheima.policydailyagent.domain.search.SearchTaskSourceFailure;
import com.itheima.policydailyagent.domain.search.SearchTaskStatus;
import com.itheima.policydailyagent.dto.SearchTaskRoundView;
import com.itheima.policydailyagent.dto.SearchTaskRunResult;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.SearchTaskPolicyRepository;
import com.itheima.policydailyagent.repository.SearchTaskRoundRepository;
import com.itheima.policydailyagent.repository.SearchTaskSourceFailureRepository;
import com.itheima.policydailyagent.service.SearchTaskService;
import com.itheima.policydailyagent.service.memory.AgentTaskMemoryService;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryAssembler;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 多轮政策搜索第一阶段驱动：读取任务 + Memory → 制定本轮规则计划 → 执行本轮搜索
 * → 保存轮次结果 → 更新候选池与 Memory → 评估主题覆盖度 → 按停止规则决定继续或结束。
 *
 * <p>第一阶段不使用 LLM 生成 Query，关键词扩展来自可配置主题词典；搜索始终只把结果
 * 写入候选池（新政策 PENDING），绝不自动 ACCEPTED、绝不触发政策分析或月报。
 *
 * <p>恢复边界（单机部署，无消息队列）：靠 search_task 上的执行租约（executor_id +
 * lease_expires_at）区分「仍在执行」与「进程崩溃后中断」；恢复时用原子条件 UPDATE
 * 抢占执行权；终止原因（termination_reason）区分「正常完成不可恢复」与「失败不可恢复」。
 *
 * <p>旧执行器隔离：每轮开始前与轮次结果落库前均做原子续约（fencing），续约或收尾时
 * 失去执行权则抛 {@link LeaseLostException} 立即终止，绝不覆盖新执行器的执行权与结果。
 */
@Service
public class SearchRoundService {

    private static final Logger log = LoggerFactory.getLogger(SearchRoundService.class);

    /** 执行租约时长（秒）。同步执行期间每轮续约，崩溃后到期即可被恢复接管。 */
    private static final long LEASE_SECONDS = 1800L;

    /** 每个逻辑轮次允许的最大重试次数（不含首次尝试）。中断重试不消耗逻辑轮次预算，单独设此上限。 */
    private static final int MAX_ROUND_RETRIES = 3;

    private final SearchTaskService searchTaskService;
    private final AgentTaskMemoryService memoryService;
    private final SearchTaskMemoryAssembler assembler;
    private final SearchRoundExecutor roundExecutor;
    private final SearchTaskRoundRepository roundRepository;
    private final SearchTaskPolicyRepository associationRepository;
    private final PolicyDocumentRepository documentRepository;
    private final SearchTaskSourceFailureRepository sourceFailureRepository;
    private final SearchRoundPlanner planner;
    private final TopicCoverageEvaluator coverageEvaluator;
    private final SearchStopPolicy stopPolicy;
    private final MultiRoundConfig config;
    private final Clock clock;
    private final SearchFeedbackService feedbackService;

    @Autowired
    public SearchRoundService(
            SearchTaskService searchTaskService,
            AgentTaskMemoryService memoryService,
            SearchTaskMemoryAssembler assembler,
            SearchRoundExecutor roundExecutor,
            SearchTaskRoundRepository roundRepository,
            SearchTaskPolicyRepository associationRepository,
            PolicyDocumentRepository documentRepository,
            SearchTaskSourceFailureRepository sourceFailureRepository,
            SearchRoundPlanner planner,
            TopicCoverageEvaluator coverageEvaluator,
            SearchStopPolicy stopPolicy,
            MultiRoundConfig config,
            Clock clock,
            SearchFeedbackService feedbackService
    ) {
        this.searchTaskService = searchTaskService;
        this.memoryService = memoryService;
        this.assembler = assembler;
        this.roundExecutor = roundExecutor;
        this.roundRepository = roundRepository;
        this.associationRepository = associationRepository;
        this.documentRepository = documentRepository;
        this.sourceFailureRepository = sourceFailureRepository;
        this.planner = planner;
        this.coverageEvaluator = coverageEvaluator;
        this.stopPolicy = stopPolicy;
        this.config = config;
        this.clock = clock;
        this.feedbackService = feedbackService;
    }

    public SearchRoundService(SearchTaskService taskService, AgentTaskMemoryService memoryService,
            SearchTaskMemoryAssembler assembler, SearchRoundExecutor executor, SearchTaskRoundRepository rounds,
            SearchTaskPolicyRepository associations, PolicyDocumentRepository documents,
            SearchTaskSourceFailureRepository failures, SearchRoundPlanner planner,
            TopicCoverageEvaluator evaluator, SearchStopPolicy stopPolicy, MultiRoundConfig config, Clock clock) {
        this(taskService, memoryService, assembler, executor, rounds, associations, documents, failures,
                planner, evaluator, stopPolicy, config, clock, null);
    }

    /**
     * 从第一轮开始执行多轮搜索。调用方已完成 createAndStart + initialize + markStarted，
     * 本方法负责：持久化参数快照、写入执行租约，并驱动后续轮次。
     */
    public SearchTaskRunResult runMultiRound(
            SearchTask task,
            List<String> keywords,
            List<String> sourceIds,
            List<PolicySourceProperties.Item> sources,
            int maxLinks,
            boolean filterByKeyword
    ) {
        SearchRunParams params = SearchRunParams.of(maxLinks, filterByKeyword, true, config);
        searchTaskService.saveRunParams(task.getId(), assembler.toJson(params));
        String executorId = newExecutorId();
        searchTaskService.markExecutor(task.getId(), executorId, leaseUntil());

        return runLoop(
                task, sourceIds, sources, config, maxLinks, filterByKeyword,
                1, 0, new RoundPlan(keywords, sourceIds), executorId, RecoveryState.empty()
        );
    }

    /**
     * 显式恢复：区分「活跃执行」「中断」「已正常完成」「不可恢复失败」四种状态，
     * 仅允许中断任务恢复；恢复前原子抢占执行权（含可恢复状态条件，消除状态校验与抢占的竞争），
     * 并从持久化轮次记录 + 候选关联 + 信源失败表重建累计状态（不清空历史结果），
     * 再按原始参数快照继续。中断（PLANNED/RUNNING/INTERRUPTED）轮次以 retry_no+1 原地重试，
     * 而非跳到 round_no+1 丢弃原始计划。
     */
    public SearchTaskRunResult resume(Long taskId) {
        if (!config.enabled()) {
            throw new IllegalStateException("多轮搜索未启用，无法恢复该任务");
        }
        SearchTask task = searchTaskService.getTask(taskId);
        validateResumable(task);

        List<SearchTaskRound> rounds = roundRepository.findBySearchTaskIdOrderByRoundNoAsc(taskId);
        if (rounds.isEmpty()) {
            throw new IllegalStateException("该任务没有轮次记录，无法恢复");
        }

        SearchRunParams params = parseParams(task.getRunParamsJson());

        String executorId = newExecutorId();
        if (!searchTaskService.claimExecutor(taskId, executorId, leaseUntil(), LocalDateTime.now(clock))) {
            throw new IllegalStateException("任务正在被其他执行器恢复，请稍后重试");
        }
        task = searchTaskService.getTask(taskId);

        List<String> sourceIds = splitCsv(task.getSourceIds());
        List<PolicySourceProperties.Item> sources = roundExecutor.resolveSources(sourceIds);
        MultiRoundConfig effectiveConfig = params.toConfig();
        RecoveryState state = rebuild(taskId, rounds, effectiveConfig);

        if (state.terminalReason() != null) {
            return finalize(task, sourceIds, state.candidateIds(), state.sourceFailures(),
                    state.executedPlans(), state.completedRounds(), state.consecutiveNoGrowth(),
                    state.lastCoverage(), state.terminalReason(),
                    state.totalFound(), state.totalFiltered(), state.totalFailed(),
                    List.of(), executorId, state.statsIncomplete());
        }
        if (state.retryNo() > MAX_ROUND_RETRIES) {
            return finalize(task, sourceIds, state.candidateIds(), state.sourceFailures(),
                    state.executedPlans(), state.completedRounds(), state.consecutiveNoGrowth(),
                    state.lastCoverage(), "RETRY_LIMIT_EXCEEDED",
                    state.totalFound(), state.totalFiltered(), state.totalFailed(),
                    List.of(), executorId, state.statsIncomplete());
        }

        return runLoop(
                task, sourceIds, sources, effectiveConfig,
                params.maxLinksPerSource(), params.filterByKeyword(),
                state.nextRoundNo(), state.retryNo(), state.resumePlan(), executorId, state
        );
    }

    @Transactional(readOnly = true)
    public List<SearchTaskRoundView> rounds(Long taskId) {
        searchTaskService.getTask(taskId);
        return roundRepository.findBySearchTaskIdOrderByRoundNoAsc(taskId).stream()
                .map(this::toView)
                .toList();
    }

    private SearchTaskRunResult runLoop(
            SearchTask task,
            List<String> allSourceIds,
            List<PolicySourceProperties.Item> allSources,
            MultiRoundConfig effectiveConfig,
            int maxLinks,
            boolean filterByKeyword,
            int firstRoundNo,
            int retryNo,
            RoundPlan initialPlan,
            String executorId,
            RecoveryState initial
    ) {
        int totalFound = initial.totalFound();
        int totalSaved = initial.totalSaved();
        int totalDuplicate = initial.totalDuplicate();
        int totalFiltered = initial.totalFiltered();
        int totalFailed = initial.totalFailed();
        boolean statsIncomplete = initial.statsIncomplete();
        List<String> messages = new ArrayList<>();
        LinkedHashSet<Long> allCandidatePolicyIds = new LinkedHashSet<>(initial.candidateIds());
        Map<String, SearchTaskMemoryContext.SourceFailure> allSourceFailures =
                new LinkedHashMap<>(initial.sourceFailures());
        Set<String> executedKeywords = new LinkedHashSet<>(initial.executedKeywords());
        List<SearchTaskMemoryContext.ExecutedPlan> executedPlans = new ArrayList<>(initial.executedPlans());
        List<Integer> completedRounds = new ArrayList<>(initial.completedRounds());
        int consecutiveNoGrowth = initial.consecutiveNoGrowth();
        List<TopicCoverageResult> lastCoverage = initial.lastCoverage();
        SearchFeedback lastFeedback = initial.lastFeedback();
        Set<String> executedSignatures = new HashSet<>();
        for (SearchTaskMemoryContext.ExecutedPlan plan : executedPlans) {
            executedSignatures.add(signatureOf(plan.keywords(), plan.sources()));
        }

        int currentRound = firstRoundNo;
        int currentRetry = retryNo;
        RoundPlan pendingInitialPlan = initialPlan;
        String stopReason = null;
        SearchTaskRound lastExecutedRound = null;

        while (true) {
            final int roundNo = currentRound;

            // 失败信源从全量信源中排除，得到本轮可用信源。
            Set<String> failedSourceIds = allSourceFailures.keySet();
            List<PolicySourceProperties.Item> availableSources = allSources.stream()
                    .filter(source -> !failedSourceIds.contains(source.getId()))
                    .toList();
            List<String> availableSourceIds = availableSources.stream()
                    .map(PolicySourceProperties.Item::getId)
                    .toList();

            // 执行前停止检查：无可用信源或已达上限/覆盖/无增长，均不再执行新轮次。
            if (availableSourceIds.isEmpty()) {
                stopReason = "NO_AVAILABLE_SOURCE";
                break;
            }
            Optional<String> preStop = stopPolicy.checkBeforeExecution(
                    roundNo, effectiveConfig.maxRounds(), lastCoverage,
                    effectiveConfig.noGrowthRounds(), consecutiveNoGrowth,
                    lastFeedback, effectiveConfig.materialSufficiencyEnabled());
            if (preStop.isPresent()) {
                stopReason = preStop.get();
                break;
            }

            RoundPlan plan;
            if (roundNo == firstRoundNo && pendingInitialPlan != null) {
                // 恢复中断轮次：复用原始计划（关键词/信源），仅按当前可用信源过滤失败信源。
                List<String> filteredSources = pendingInitialPlan.sourceIds().stream()
                        .filter(availableSourceIds::contains)
                        .toList();
                plan = new RoundPlan(pendingInitialPlan.keywords(), filteredSources);
            } else {
                Optional<RoundPlan> next = effectiveConfig.semanticFeedbackEnabled()
                        ? planner.planFromFeedback(lastFeedback == null
                                ? new SearchFeedback("RULE_FALLBACK", null, lastCoverage, List.of(), "恢复时无语义反馈")
                                : lastFeedback, executedPlans, effectiveConfig.topics(), availableSourceIds,
                                effectiveConfig.maxKeywordsPerRound(), effectiveConfig.materialSufficiencyEnabled())
                        : planner.planNextRound(lastCoverage, new ArrayList<>(executedKeywords),
                                effectiveConfig.topics(), availableSourceIds, effectiveConfig.maxKeywordsPerRound());
                if (next.isEmpty()) {
                    stopReason = "NO_NEW_PLAN";
                    break;
                }
                plan = next.get();
            }
            if (plan.sourceIds().isEmpty()) {
                stopReason = "NO_AVAILABLE_SOURCE";
                break;
            }
            if (executedSignatures.contains(plan.signature())) {
                stopReason = "NO_NEW_PLAN";
                break;
            }

            // FENCE：每轮开始前续约。失去执行权时抛 LeaseLostException，立即终止后续采集与状态推进。
            renewLease(task.getId(), executorId);

            int thisRetry = (roundNo == firstRoundNo) ? currentRetry : 0;

            SearchTaskRound round = new SearchTaskRound();
            round.setSearchTaskId(task.getId());
            round.setRoundNo(roundNo);
            round.setRetryNo(thisRetry);
            round.setStatus(SearchTaskRoundStatus.PLANNED);
            round.setPlanJson(assembler.toJson(plan));
            round.setKeywordsJson(assembler.toJson(plan.keywords()));
            round.setTargetSourcesJson(assembler.toJson(plan.sourceIds()));
            round.setCoverageBeforeJson(assembler.toJson(lastCoverage));
            final SearchTaskRound plannedToSave = round;
            try {
                round = searchTaskService.withOwnership(task.getId(), executorId,
                        () -> roundRepository.save(plannedToSave));
            } catch (LeaseLostException e) {
                throw e;
            } catch (Exception e) {
                log.error("第 {} 轮创建 PLANNED 记录失败: taskId={}, error={}",
                        roundNo, task.getId(), e.getMessage(), e);
                stopReason = "TASK_ERROR";
                break;
            }

            safeMemory(task.getId(), "recordRoundPlanned",
                    () -> memoryService.recordRoundPlanned(task.getId(), roundNo, plan.keywords(), plan.sourceIds()));

            round.setStatus(SearchTaskRoundStatus.RUNNING);
            round.setStartedAt(LocalDateTime.now(clock));
            final SearchTaskRound runningToSave = round;
            try {
                round = searchTaskService.withOwnership(task.getId(), executorId,
                        () -> roundRepository.save(runningToSave));
            } catch (LeaseLostException e) {
                throw e;
            } catch (Exception e) {
                log.error("第 {} 轮更新 RUNNING 失败: taskId={}, error={}",
                        roundNo, task.getId(), e.getMessage(), e);
                stopReason = "TASK_ERROR";
                break;
            }
            safeMemory(task.getId(), "recordRoundStarted",
                    () -> memoryService.recordRoundStarted(task.getId(), roundNo));

            RoundExecution execution;
            try {
                execution = roundExecutor.execute(
                        task, plan.keywords(), availableSources.stream()
                                .filter(source -> plan.sourceIds().contains(source.getId())).toList(),
                        maxLinks, filterByKeyword, roundNo, executorId);
            } catch (LeaseLostException e) {
                throw e;
            } catch (SourceFailureCheckpointException e) {
                // 信源失败检查点未能可靠落库：绝不吞掉后继续宣称检查点已保存。进入可识别
                // 中断状态（CHECKPOINT_FAILED），数据库恢复后可重新运行以重试该检查点的持久化。
                log.error("第 {} 轮信源失败检查点写入失败（进入可识别中断状态）: taskId={}, error={}",
                        roundNo, task.getId(), e.getMessage(), e);
                round.setStatus(SearchTaskRoundStatus.FAILED);
                round.setStopReason("CHECKPOINT_FAILED");
                round.setCompletedAt(LocalDateTime.now(clock));
                markRoundFailed(round, task.getId(), roundNo, executorId);
                stopReason = "CHECKPOINT_FAILED";
                statsIncomplete = true;
                break;
            } catch (Exception e) {
                log.error("第 {} 轮执行遇到不可恢复错误: taskId={}, error={}",
                        roundNo, task.getId(), e.getMessage(), e);
                round.setStatus(SearchTaskRoundStatus.FAILED);
                round.setStopReason("TASK_ERROR");
                round.setCompletedAt(LocalDateTime.now(clock));
                markRoundFailed(round, task.getId(), roundNo, executorId);
                stopReason = "TASK_ERROR";
                break;
            }

            totalFound += execution.found();
            totalSaved += execution.saved();
            totalDuplicate += execution.duplicate();
            totalFiltered += execution.filtered();
            totalFailed += execution.failed();
            messages.addAll(execution.messages());
            allCandidatePolicyIds.addAll(execution.candidatePolicyIds());
            for (SearchTaskMemoryContext.SourceFailure failure : execution.sourceFailures()) {
                allSourceFailures.put(failure.sourceId(), failure);
            }

            executedKeywords.addAll(plan.keywords());
            executedPlans.add(new SearchTaskMemoryContext.ExecutedPlan(plan.keywords(), plan.sourceIds()));
            executedSignatures.add(plan.signature());
            completedRounds.add(roundNo);

            if (execution.associated() == 0) {
                consecutiveNoGrowth++;
            } else {
                consecutiveNoGrowth = 0;
            }

            List<TopicCoverageResult> coverage;
            try {
                if (effectiveConfig.semanticFeedbackEnabled() && feedbackService != null) {
                    renewLease(task.getId(), executorId);
                    lastFeedback = feedbackService.evaluate(loadCandidates(task.getId()), effectiveConfig,
                            List.copyOf(executedPlans), Map.of(
                                    "reportMonth", task.getReportMonth() == null ? "" : task.getReportMonth(),
                                    "targetStartDate", String.valueOf(task.getTargetStartDate()),
                                    "targetEndDate", String.valueOf(task.getTargetEndDate())));
                    renewLease(task.getId(), executorId);
                    coverage = lastFeedback.coverage();
                } else {
                    coverage = evaluateCoverage(task.getId(), effectiveConfig);
                }
            } catch (LeaseLostException e) {
                throw e;
            } catch (Exception e) {
                log.error("第 {} 轮覆盖度评估失败: taskId={}, error={}",
                        roundNo, task.getId(), e.getMessage(), e);
                round.setStatus(SearchTaskRoundStatus.FAILED);
                round.setStopReason("TASK_ERROR");
                round.setCompletedAt(LocalDateTime.now(clock));
                markRoundFailed(round, task.getId(), roundNo, executorId);
                stopReason = "TASK_ERROR";
                break;
            }
            lastCoverage = coverage;

            // FENCE：轮次结果落库前再次续约，防止慢执行期间租约到期后被接管、旧执行器写入过期轮次结果。
            renewLease(task.getId(), executorId);

            final SearchTaskRound completedToSave = round;
            try {
                round.setStatus(execution.sourceFailures().isEmpty()
                        ? SearchTaskRoundStatus.COMPLETED
                        : SearchTaskRoundStatus.PARTIAL_FAILED);
                round.setFoundCount(execution.found());
                round.setSavedCount(execution.saved());
                round.setDuplicateCount(execution.duplicate());
                round.setFilteredCount(execution.filtered());
                round.setFailedCount(execution.failed());
                round.setNewAssociationCount(execution.associated());
                round.setCoverageAfterJson(assembler.toJson(coverage));
                round.setSearchFeedbackJson(lastFeedback == null ? null : assembler.toJson(lastFeedback));
                round.setCompletedAt(LocalDateTime.now(clock));
                round = searchTaskService.withOwnership(task.getId(), executorId,
                        () -> roundRepository.save(completedToSave));
            } catch (LeaseLostException e) {
                throw e;
            } catch (Exception e) {
                log.error("第 {} 轮记录持久化失败: taskId={}, error={}",
                        roundNo, task.getId(), e.getMessage(), e);
                round.setStatus(SearchTaskRoundStatus.FAILED);
                round.setStopReason("TASK_ERROR");
                round.setCompletedAt(LocalDateTime.now(clock));
                markRoundFailed(round, task.getId(), roundNo, executorId);
                stopReason = "TASK_ERROR";
                break;
            }
            lastExecutedRound = round;

            final SearchFeedback recordedFeedback = lastFeedback;
            if (recordedFeedback != null) {
                safeMemory(task.getId(), "recordSearchFeedback",
                        () -> memoryService.recordSearchFeedback(task.getId(), roundNo, recordedFeedback));
            }

            List<SearchTaskMemoryContext.TopicCoverage> memoryCoverage = toMemoryCoverage(coverage);
            safeMemory(task.getId(), "recordCoverageEvaluated",
                    () -> memoryService.recordCoverageEvaluated(task.getId(), roundNo, memoryCoverage));

            SearchTaskMemoryContext snapshot = assembler.contextOf(
                    task,
                    execution.executedSources(),
                    List.copyOf(allCandidatePolicyIds),
                    SearchTaskMemoryContext.Counts.of(totalFound, totalSaved, totalDuplicate, totalFiltered, totalFailed),
                    List.copyOf(allSourceFailures.values()),
                    roundNo,
                    List.copyOf(completedRounds),
                    List.copyOf(executedPlans),
                    memoryCoverage,
                    consecutiveNoGrowth,
                    null
            ).withFeedback(lastFeedback);
            safeMemory(task.getId(), "recordRoundCompleted",
                    () -> memoryService.recordRoundCompleted(task.getId(), roundNo, snapshot));

            currentRound = roundNo + 1;
            currentRetry = 0;
            pendingInitialPlan = null;
        }

        if (stopReason != null && lastExecutedRound != null && lastExecutedRound.getStopReason() == null) {
            final SearchTaskRound stopReasonRound = lastExecutedRound;
            final String finalStopReason = stopReason;
            try {
                searchTaskService.withOwnership(task.getId(), executorId, () -> {
                    stopReasonRound.setStopReason(finalStopReason);
                    return roundRepository.save(stopReasonRound);
                });
            } catch (LeaseLostException e) {
                throw e;
            } catch (Exception e) {
                log.warn("第 {} 轮最终停止原因持久化失败（不影响任务收尾）: taskId={}, error={}",
                        lastExecutedRound.getRoundNo(), task.getId(), e.getMessage(), e);
            }
        }

        return finalize(task, allSourceIds, allCandidatePolicyIds, allSourceFailures, executedPlans,
                completedRounds, consecutiveNoGrowth, lastCoverage, stopReason,
                totalFound, totalFiltered, totalFailed, messages, executorId, statsIncomplete);
    }

    private SearchTaskRunResult finalize(
            SearchTask task,
            List<String> sourceIds,
            Set<Long> allCandidatePolicyIds,
            Map<String, SearchTaskMemoryContext.SourceFailure> allSourceFailures,
            List<SearchTaskMemoryContext.ExecutedPlan> executedPlans,
            List<Integer> completedRounds,
            int consecutiveNoGrowth,
            List<TopicCoverageResult> lastCoverage,
            String stopReason,
            int totalFound,
            int totalFiltered,
            int totalFailed,
            List<String> messages,
            String executorId,
            boolean countsIncomplete
    ) {
        // 任务级 saved / duplicate / associated 从业务事实（policy_document.search_task_id +
        // search_task_policy）重建，而非轮次记录求和：中断重试后，首次尝试已入库的候选在
        // 重试时会按「已存在」判为 duplicate，若按物理尝试求和会把首次的 saved 抹掉。
        CandidateCounts candidate = reconstructCandidateCounts(task.getId());
        int saved = candidate.saved();
        int duplicate = candidate.duplicate();
        int associated = candidate.associated();

        String terminationReason = stopReason != null ? stopReason : "NO_NEW_PLAN";
        int currentRound = completedRounds.isEmpty() ? 0 : completedRounds.get(completedRounds.size() - 1);
        SearchTaskMemoryContext finalSnapshot = assembler.contextOf(
                task,
                sourceIds,
                List.copyOf(allCandidatePolicyIds),
                SearchTaskMemoryContext.Counts.of(totalFound, saved, duplicate, totalFiltered, totalFailed),
                List.copyOf(allSourceFailures.values()),
                currentRound,
                List.copyOf(completedRounds),
                List.copyOf(executedPlans),
                toMemoryCoverage(lastCoverage),
                consecutiveNoGrowth,
                terminationReason
        ).withFeedback(loadLatestFeedback(task.getId()));

        boolean unrecoverable = "TASK_ERROR".equals(stopReason)
                || "NO_AVAILABLE_SOURCE".equals(stopReason)
                || "RETRY_LIMIT_EXCEEDED".equals(stopReason)
                || "CHECKPOINT_FAILED".equals(stopReason);

        SearchTask finished;
        try {
            if (unrecoverable) {
                String message = buildFailureMessage(stopReason, messages);
                finished = searchTaskService.failWithCounts(
                        task.getId(), executorId, totalFound, saved, duplicate, totalFiltered, totalFailed,
                        message, terminationReason);
                safeMemory(task.getId(), "markFailed",
                        () -> memoryService.markFailed(task.getId(), finalSnapshot, message,
                                "检查固定信源可用性后重新运行搜索任务"));
            } else {
                finished = searchTaskService.completeWithReason(
                        task.getId(), executorId, totalFound, saved, duplicate, totalFiltered, totalFailed,
                        terminationReason);
                safeMemory(task.getId(), "markCompleted",
                        () -> memoryService.markCompleted(task.getId(), finalSnapshot));
            }
        } catch (LeaseLostException e) {
            // 失去执行权：不覆盖新执行器结果，也不写 Memory。
            throw e;
        } catch (Exception e) {
            log.error("任务最终收尾失败（DB 不可用时任务保持 RUNNING，恢复后可识别中断状态）: taskId={}, stopReason={}, error={}",
                    task.getId(), stopReason, e.getMessage(), e);
            throw e;
        }

        return new SearchTaskRunResult(
                finished.getId(),
                finished.getStatus(),
                totalFound,
                saved,
                duplicate,
                totalFiltered,
                totalFailed,
                associated,
                completedRounds.size(),
                List.copyOf(messages),
                countsIncomplete
        );
    }

    /**
     * 从业务事实重建任务级候选计数：saved = 本任务新建（policy_document.search_task_id == taskId）
     * 的候选数；associated = 与任务关联的唯一候选数（search_task_policy 去重）；duplicate =
     * associated - saved（已存在、被再次关联的候选数）。三者与轮次记录无关，中断重试后仍精确。
     */
    private CandidateCounts reconstructCandidateCounts(Long taskId) {
        List<SearchTaskPolicy> associations =
                associationRepository.findBySearchTaskIdOrderByDiscoveryOrderAscIdAsc(taskId);
        long associated = associations.stream()
                .map(SearchTaskPolicy::getPolicyId)
                .distinct()
                .count();
        long saved = documentRepository.countBySearchTaskId(taskId);
        long duplicate = Math.max(0, associated - saved);
        return new CandidateCounts((int) saved, (int) duplicate, (int) associated);
    }

    private record CandidateCounts(int saved, int duplicate, int associated) {
    }

    private void validateResumable(SearchTask task) {
        SearchTaskStatus status = task.getStatus();
        if (status == SearchTaskStatus.CREATED) {
            throw new IllegalStateException("任务尚未开始执行，无法恢复");
        }
        if (status == SearchTaskStatus.COMPLETED || status == SearchTaskStatus.PARTIAL_FAILED) {
            throw new IllegalStateException("任务已正常完成，无法恢复");
        }
        if (status == SearchTaskStatus.FAILED) {
            throw new IllegalStateException("任务已失败且不可恢复，请重新运行搜索任务");
        }
        boolean leaseActive = task.getExecutorId() != null
                && task.getLeaseExpiresAt() != null
                && task.getLeaseExpiresAt().isAfter(LocalDateTime.now(clock));
        if (leaseActive) {
            throw new IllegalStateException("任务正在执行中，无法恢复");
        }
    }

    private SearchRunParams parseParams(String json) {
        SearchRunParams params = assembler.parseJson(json, SearchRunParams.class);
        if (params == null) {
            throw new IllegalStateException("该任务缺少执行参数快照，无法可靠恢复，请重新运行搜索任务");
        }
        if (params.version() < 1 || params.version() > SearchRunParams.CURRENT_VERSION) {
            throw new IllegalStateException("该任务的执行参数快照版本不兼容，无法恢复，请重新运行搜索任务");
        }
        if (!params.multiRoundEnabled()) {
            throw new IllegalStateException("该任务不是多轮搜索任务，无法恢复");
        }
        return params;
    }

    /**
     * 从持久化轮次记录 + 候选关联 + 信源失败表重建累计状态。
     * 候选 ID 以 search_task_policy 为准（去重全集），计数以「已完成」轮次记录求和为准，
     * 失败信源以 search_task_source_failure 为准。中断轮次（PLANNED/RUNNING/FAILED-INTERRUPTED）
     * 不并入已执行关键词/计划，而是以「原始计划 + retry_no+1」原地重试，绝不跳过。
     */
    private RecoveryState rebuild(
            Long taskId,
            List<SearchTaskRound> rounds,
            MultiRoundConfig effectiveConfig
    ) {
        // 按逻辑轮次分组，取每组的最后一次尝试（retry_no 最大）作为该逻辑轮次的权威状态。
        Map<Integer, SearchTaskRound> latestAttemptByRoundNo = new LinkedHashMap<>();
        for (SearchTaskRound round : rounds) {
            SearchTaskRound existing = latestAttemptByRoundNo.get(round.getRoundNo());
            if (existing == null || round.getRetryNo() > existing.getRetryNo()) {
                latestAttemptByRoundNo.put(round.getRoundNo(), round);
            }
        }

        int totalFound = 0;
        int totalSaved = 0;
        int totalDuplicate = 0;
        int totalFiltered = 0;
        int totalFailed = 0;
        Set<String> executedKeywords = new LinkedHashSet<>();
        List<SearchTaskMemoryContext.ExecutedPlan> executedPlans = new ArrayList<>();
        List<Integer> completedRounds = new ArrayList<>();
        int consecutiveNoGrowth = 0;
        int lastCompletedRoundNo = 0;
        int pendingRoundNo = -1;
        int pendingRetryNo = 0;
        RoundPlan pendingPlan = null;
        String terminalReason = null;
        boolean statsIncomplete = false;
        SearchFeedback lastFeedback = null;

        for (SearchTaskRound round : latestAttemptByRoundNo.values()) {
            List<String> keywords = assembler.parseStringList(round.getKeywordsJson());
            List<String> roundSources = assembler.parseStringList(round.getTargetSourcesJson());
            SearchTaskRoundStatus status = round.getStatus();

            if (status == SearchTaskRoundStatus.COMPLETED
                    || status == SearchTaskRoundStatus.PARTIAL_FAILED) {
                executedKeywords.addAll(keywords);
                executedPlans.add(new SearchTaskMemoryContext.ExecutedPlan(keywords, roundSources));
                completedRounds.add(round.getRoundNo());
                lastCompletedRoundNo = Math.max(lastCompletedRoundNo, round.getRoundNo());
                lastFeedback = assembler.parseJson(round.getSearchFeedbackJson(), SearchFeedback.class);
                totalFound += round.getFoundCount();
                totalSaved += round.getSavedCount();
                totalDuplicate += round.getDuplicateCount();
                totalFiltered += round.getFilteredCount();
                totalFailed += round.getFailedCount();
                if (round.getNewAssociationCount() == 0) {
                    consecutiveNoGrowth++;
                } else {
                    consecutiveNoGrowth = 0;
                }
            } else if (status == SearchTaskRoundStatus.PLANNED
                    || status == SearchTaskRoundStatus.RUNNING) {
                // 中断轮次：标记 FAILED(INTERRUPTED) 后原地重试原始计划，不并入已执行集合。
                // 该轮次的物理尝试计数（found/filtered/failed）无法精确还原，标记统计不完整。
                round.setStatus(SearchTaskRoundStatus.FAILED);
                round.setStopReason("INTERRUPTED");
                round.setCompletedAt(LocalDateTime.now(clock));
                roundRepository.save(round);
                pendingRoundNo = round.getRoundNo();
                pendingRetryNo = round.getRetryNo() + 1;
                pendingPlan = new RoundPlan(keywords, roundSources);
                statsIncomplete = true;
            } else {
                // FAILED：INTERRUPTED 可重试；其余（TASK_ERROR/RETRY_LIMIT_EXCEEDED 等）为不可恢复终态。
                if ("INTERRUPTED".equals(round.getStopReason())) {
                    pendingRoundNo = round.getRoundNo();
                    pendingRetryNo = round.getRetryNo() + 1;
                    pendingPlan = new RoundPlan(keywords, roundSources);
                    statsIncomplete = true;
                } else {
                    terminalReason = round.getStopReason() == null ? "TASK_ERROR" : round.getStopReason();
                }
            }
        }

        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();
        for (SearchTaskPolicy association :
                associationRepository.findBySearchTaskIdOrderByDiscoveryOrderAscIdAsc(taskId)) {
            candidateIds.add(association.getPolicyId());
        }

        Map<String, SearchTaskMemoryContext.SourceFailure> sourceFailures = new LinkedHashMap<>();
        for (SearchTaskSourceFailure failure :
                sourceFailureRepository.findBySearchTaskIdOrderByFirstRoundNoAsc(taskId)) {
            sourceFailures.put(failure.getSourceId(),
                    new SearchTaskMemoryContext.SourceFailure(
                            failure.getSourceId(), failure.getSourceName(), failure.getMessage()));
        }

        List<TopicCoverageResult> lastCoverage = effectiveConfig.semanticFeedbackEnabled()
                ? lastFeedback == null
                        ? effectiveConfig.topics().stream().map(t -> new TopicCoverageResult(t, 0,
                                CoverageStatus.NOT_COVERED, List.of())).toList()
                        : lastFeedback.coverage()
                : evaluateCoverage(taskId, effectiveConfig);

        int nextRoundNo = pendingRoundNo >= 0 ? pendingRoundNo : lastCompletedRoundNo + 1;
        return new RecoveryState(
                nextRoundNo, pendingRetryNo, pendingPlan, terminalReason,
                executedKeywords, executedPlans, completedRounds,
                consecutiveNoGrowth, lastCoverage,
                totalFound, totalSaved, totalDuplicate, totalFiltered, totalFailed,
                candidateIds, sourceFailures, statsIncomplete, lastFeedback
        );
    }

    private List<TopicCoverageResult> evaluateCoverage(Long taskId, MultiRoundConfig effectiveConfig) {
        List<CandidateText> candidates = loadCandidates(taskId);
        return coverageEvaluator.evaluate(candidates, effectiveConfig.topics(),
                effectiveConfig.coverageThreshold(), effectiveConfig.maxContentScanLength());
    }

    private List<CandidateText> loadCandidates(Long taskId) {
        List<SearchTaskPolicy> associations =
                associationRepository.findBySearchTaskIdOrderByDiscoveryOrderAscIdAsc(taskId);
        if (associations.isEmpty()) {
            return List.of();
        }
        List<Long> policyIds = associations.stream()
                .map(SearchTaskPolicy::getPolicyId)
                .distinct()
                .toList();
        return documentRepository.findAllById(policyIds).stream()
                .map(this::toCandidateText)
                .toList();
    }

    private CandidateText toCandidateText(PolicyDocument document) {
        String body = hasText(document.getCleanedContent()) ? document.getCleanedContent() : document.getContent();
        return new CandidateText(document.getId(), document.getTitle(), document.getKeywords(),
                document.getSummary(), body);
    }

    private List<SearchTaskMemoryContext.TopicCoverage> toMemoryCoverage(List<TopicCoverageResult> coverage) {
        return (coverage == null ? List.<TopicCoverageResult>of() : coverage).stream()
                .map(result -> new SearchTaskMemoryContext.TopicCoverage(
                        result.topic(), result.matchedCandidateCount(), result.status().name(),
                        result.evidencePolicyIds()))
                .toList();
    }

    private SearchTaskRoundView toView(SearchTaskRound round) {
        return new SearchTaskRoundView(
                round.getId(),
                round.getSearchTaskId(),
                round.getRoundNo(),
                round.getStatus(),
                round.getPlanJson(),
                round.getKeywordsJson(),
                round.getTargetSourcesJson(),
                round.getCoverageBeforeJson(),
                round.getCoverageAfterJson(),
                round.getFoundCount(),
                round.getSavedCount(),
                round.getDuplicateCount(),
                round.getFilteredCount(),
                round.getFailedCount(),
                round.getNewAssociationCount(),
                round.getStopReason(),
                round.getStartedAt(),
                round.getCompletedAt(),
                round.getCreatedAt(),
                round.getSearchFeedbackJson()
        );
    }

    private String signatureOf(List<String> keywords, List<String> sourceIds) {
        return new RoundPlan(keywords, sourceIds).signature();
    }

    private SearchFeedback loadLatestFeedback(Long taskId) {
        return roundRepository.findBySearchTaskIdOrderByRoundNoAsc(taskId).stream()
                .filter(r -> r.getStatus() == SearchTaskRoundStatus.COMPLETED
                        || r.getStatus() == SearchTaskRoundStatus.PARTIAL_FAILED)
                .max(java.util.Comparator.comparingInt(SearchTaskRound::getRoundNo)
                        .thenComparingInt(SearchTaskRound::getRetryNo))
                .map(r -> assembler.parseJson(r.getSearchFeedbackJson(), SearchFeedback.class)).orElse(null);
    }

    private String buildFailureMessage(String stopReason, List<String> messages) {
        if ("NO_AVAILABLE_SOURCE".equals(stopReason)) {
            return "所有固定信源均采集失败，无可继续搜索的信源";
        }
        if ("RETRY_LIMIT_EXCEEDED".equals(stopReason)) {
            return "该轮次重试次数已达上限，无法继续恢复";
        }
        if ("CHECKPOINT_FAILED".equals(stopReason)) {
            return "信源失败检查点未能可靠保存，数据库恢复后重新运行搜索任务可重试其持久化";
        }
        return messages.isEmpty() ? "搜索执行过程中发生不可恢复错误" : String.join("；", messages);
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private String newExecutorId() {
        return UUID.randomUUID().toString();
    }

    private LocalDateTime leaseUntil() {
        return LocalDateTime.now(clock).plusSeconds(LEASE_SECONDS);
    }

    /**
     * 原子续约（fencing）。执行权已丢失时抛 {@link LeaseLostException}，调用方必须立即终止。
     */
    private void renewLease(Long taskId, String executorId) {
        if (!searchTaskService.renewLease(taskId, executorId, leaseUntil())) {
            throw new LeaseLostException(taskId, executorId);
        }
    }

    /**
     * 尽力而为地把轮次标记为 FAILED。写入受执行权保护：执行权已丢失时跳过写入（绝不覆盖
     * 新执行器的轮次记录），DB 不可用时仅记录日志（不掩盖原始错误）。由调用方在失去执行权
     * 的路径上（如 COMPLETED 落库失败）另行抛 {@link LeaseLostException} 终止。
     */
    private void markRoundFailed(SearchTaskRound round, Long taskId, int roundNo, String executorId) {
        try {
            searchTaskService.withOwnership(taskId, executorId, () -> roundRepository.save(round));
        } catch (LeaseLostException e) {
            log.warn("标记轮次失败时执行权已丢失，跳过写入: taskId={}, roundNo={}", taskId, roundNo);
        } catch (Exception saveEx) {
            log.warn("标记轮次失败（DB 可能不可用）: taskId={}, roundNo={}, error={}",
                    taskId, roundNo, saveEx.getMessage());
        }
    }

    private void safeMemory(Long taskId, String operation, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Agent Memory 记录失败（不影响搜索主流程）: taskId={}, operation={}, error={}",
                    taskId, operation, e.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 恢复重建后的累计状态。候选 ID 与失败信源是恢复的事实来源；计数来自「已完成」轮次记录求和。
     * resumePlan/retryNo 携带需原地重试的中断轮次原始计划；terminalReason 非空表示存在不可恢复失败轮次。
     */
    private record RecoveryState(
            int nextRoundNo,
            int retryNo,
            RoundPlan resumePlan,
            String terminalReason,
            Set<String> executedKeywords,
            List<SearchTaskMemoryContext.ExecutedPlan> executedPlans,
            List<Integer> completedRounds,
            int consecutiveNoGrowth,
            List<TopicCoverageResult> lastCoverage,
            int totalFound,
            int totalSaved,
            int totalDuplicate,
            int totalFiltered,
            int totalFailed,
            LinkedHashSet<Long> candidateIds,
            Map<String, SearchTaskMemoryContext.SourceFailure> sourceFailures,
            boolean statsIncomplete,
            SearchFeedback lastFeedback
    ) {
        static RecoveryState empty() {
            return new RecoveryState(
                    1, 0, null, null,
                    new LinkedHashSet<>(), new ArrayList<>(), new ArrayList<>(),
                    0, List.of(), 0, 0, 0, 0, 0,
                    new LinkedHashSet<>(), new HashMap<>(), false, null
            );
        }
    }
}
