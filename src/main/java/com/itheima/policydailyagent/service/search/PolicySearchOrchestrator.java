package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.config.PolicySourceProperties;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.dto.SearchTaskRunRequest;
import com.itheima.policydailyagent.dto.SearchTaskRunResult;
import com.itheima.policydailyagent.service.SearchTaskService;
import com.itheima.policydailyagent.service.memory.AgentTaskMemoryService;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

/**
 * 政策搜索编排器。负责请求校验、任务创建与启动、Memory 初始化，以及单轮/多轮模式的分发。
 *
 * <p>单轮模式保持原有行为（用户关键词 + 用户信源，一次性执行）；多轮模式由
 * {@link SearchRoundService} 驱动，仅在配置启用且请求显式声明时生效（默认关闭）。
 * 采集循环本身下沉到 {@link SearchRoundExecutor}，避免在编排器内重复堆叠。
 */
@Service
public class PolicySearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PolicySearchOrchestrator.class);

    private final SearchTaskService searchTaskService;
    private final AgentTaskMemoryService memoryService;
    private final SearchRoundExecutor roundExecutor;
    private final SearchRoundService roundService;
    private final MultiRoundConfig multiRoundConfig;

    public PolicySearchOrchestrator(
            SearchTaskService searchTaskService,
            AgentTaskMemoryService memoryService,
            SearchRoundExecutor roundExecutor,
            SearchRoundService roundService,
            MultiRoundConfig multiRoundConfig
    ) {
        this.searchTaskService = searchTaskService;
        this.memoryService = memoryService;
        this.roundExecutor = roundExecutor;
        this.roundService = roundService;
        this.multiRoundConfig = multiRoundConfig;
    }

    public SearchTaskRunResult run(SearchTaskRunRequest request) {
        validate(request);
        List<String> keywords = normalizeKeywords(request.keywords());
        List<String> sourceIds = request.sourceIds().stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        List<PolicySourceProperties.Item> sources = roundExecutor.resolveSources(sourceIds);
        int maxLinks = Math.min(Math.max(request.maxLinksPerSource() == null
                ? 10 : request.maxLinksPerSource(), 1), 50);
        boolean filterByKeyword = request.filterByKeyword() == null || request.filterByKeyword();

        SearchTask task = searchTaskService.createAndStart(request, keywords, sourceIds);
        safeMemory(task.getId(), "initialize", () -> memoryService.initialize(task));
        safeMemory(task.getId(), "markStarted", () -> memoryService.markStarted(task.getId()));

        if (multiRoundConfig.enabled() && Boolean.TRUE.equals(request.multiRoundEnabled())) {
            return roundService.runMultiRound(task, keywords, sourceIds, sources, maxLinks, filterByKeyword);
        }

        RoundExecution execution = roundExecutor.execute(task, keywords, sources, maxLinks, filterByKeyword, 1, null);
        SearchTaskMemoryContext finalSnapshot = memoryService.buildContext(
                task,
                execution.executedSources(),
                execution.candidatePolicyIds(),
                SearchTaskMemoryContext.Counts.of(
                        execution.found(),
                        execution.saved(),
                        execution.duplicate(),
                        execution.filtered(),
                        execution.failed()
                ),
                execution.sourceFailures()
        );
        safeMemory(task.getId(), "recordRoundCompleted",
                () -> memoryService.recordRoundCompleted(task.getId(), finalSnapshot));

        SearchTask finished;
        if (execution.successfulSources() == 0) {
            String message = execution.messages().isEmpty()
                    ? "所有信源均采集失败"
                    : String.join("；", execution.messages());
            finished = searchTaskService.fail(task.getId(), execution.found(), execution.failed(), message);
            safeMemory(task.getId(), "markFailed", () -> memoryService.markFailed(
                    task.getId(), finalSnapshot, message, "检查固定信源可用性后重新运行搜索任务"));
        } else {
            finished = searchTaskService.complete(
                    task.getId(),
                    execution.found(),
                    execution.saved(),
                    execution.duplicate(),
                    execution.filtered(),
                    execution.failed()
            );
            safeMemory(task.getId(), "markCompleted",
                    () -> memoryService.markCompleted(task.getId(), finalSnapshot));
        }

        return new SearchTaskRunResult(
                finished.getId(),
                finished.getStatus(),
                execution.found(),
                execution.saved(),
                execution.duplicate(),
                execution.filtered(),
                execution.failed(),
                execution.associated(),
                1,
                List.copyOf(execution.messages()),
                false
        );
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null || keywords.stream().noneMatch(this::hasText)) {
            return SearchTopicDictionary.DEFAULT_TOPICS;
        }
        return keywords.stream().filter(this::hasText).map(String::trim).distinct().toList();
    }

    private void safeMemory(Long taskId, String operation, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Agent Memory 记录失败（不影响搜索主流程）: taskId={}, operation={}, error={}",
                    taskId, operation, rootMessage(e));
        }
    }

    private void validate(SearchTaskRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("搜索任务请求不能为空");
        }
        if (request.targetStartDate() == null || request.targetEndDate() == null) {
            throw new IllegalArgumentException("搜索开始日期和结束日期不能为空");
        }
        if (request.targetStartDate().isAfter(request.targetEndDate())) {
            throw new IllegalArgumentException("搜索开始日期不能晚于结束日期");
        }
        if (request.sourceIds() == null || request.sourceIds().stream().noneMatch(this::hasText)) {
            throw new IllegalArgumentException("至少选择一个固定信源");
        }
        if (hasText(request.reportMonth())) {
            try {
                YearMonth.parse(request.reportMonth().trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("月报月份必须使用 yyyy-MM 格式");
            }
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return hasText(current.getMessage()) ? current.getMessage() : current.getClass().getSimpleName();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
