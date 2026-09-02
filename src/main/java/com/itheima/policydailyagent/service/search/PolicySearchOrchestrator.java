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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PolicySearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PolicySearchOrchestrator.class);

    private static final List<String> DEFAULT_KEYWORDS = List.of(
            "人工智能", "大模型", "智能体", "人工智能+", "数据集", "算力",
            "智能制造", "工业互联网", "数字化转型", "软件和信息技术"
    );

    private final PolicySourceProperties sourceProperties;
    private final List<PolicySiteAdapter> siteAdapters;
    private final PolicyCandidateIngestService ingestService;
    private final SearchTaskService searchTaskService;
    private final AgentTaskMemoryService memoryService;

    public PolicySearchOrchestrator(
            PolicySourceProperties sourceProperties,
            List<PolicySiteAdapter> siteAdapters,
            PolicyCandidateIngestService ingestService,
            SearchTaskService searchTaskService,
            AgentTaskMemoryService memoryService
    ) {
        this.sourceProperties = sourceProperties;
        this.siteAdapters = siteAdapters;
        this.ingestService = ingestService;
        this.searchTaskService = searchTaskService;
        this.memoryService = memoryService;
    }

    public SearchTaskRunResult run(SearchTaskRunRequest request) {
        validate(request);
        List<String> keywords = normalizeKeywords(request.keywords());
        List<String> sourceIds = request.sourceIds().stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        List<PolicySourceProperties.Item> sources = resolveSources(sourceIds);
        int maxLinks = Math.min(Math.max(request.maxLinksPerSource() == null
                ? 10 : request.maxLinksPerSource(), 1), 50);
        boolean filterByKeyword = request.filterByKeyword() == null || request.filterByKeyword();

        SearchTask task = searchTaskService.createAndStart(request, keywords, sourceIds);
        MutableCounters counters = new MutableCounters();
        List<String> messages = new ArrayList<>();
        List<String> executedSources = new ArrayList<>();
        List<Long> candidatePolicyIds = new ArrayList<>();
        List<SearchTaskMemoryContext.SourceFailure> sourceFailures = new ArrayList<>();
        int successfulSources = 0;
        int discoveryOrder = 0;

        safeMemory(task.getId(), "initialize", () -> memoryService.initialize(task));
        safeMemory(task.getId(), "markStarted", () -> memoryService.markStarted(task.getId()));

        for (PolicySourceProperties.Item source : sources) {
            String sourceError = null;
            try {
                PolicySiteAdapter adapter = findAdapter(source.getUrl());
                List<PolicySiteAdapter.DiscoveredPolicyLink> links = adapter.discover(
                        source.getUrl(),
                        keywords,
                        maxLinks,
                        filterByKeyword
                );
                successfulSources++;
                counters.found += links.size();
                if (links.isEmpty()) {
                    messages.add(source.getName() + "：未发现匹配链接");
                }

                for (PolicySiteAdapter.DiscoveredPolicyLink link : links) {
                    discoveryOrder++;
                    try {
                        PolicyCandidateIngestService.IngestOutcome outcome = ingestService.ingest(
                                task,
                                source,
                                link,
                                discoveryOrder
                        );
                        if (outcome.associationCreated()) {
                            counters.associated++;
                        }
                        if (outcome.policyId() != null) {
                            candidatePolicyIds.add(outcome.policyId());
                        }
                        switch (outcome.type()) {
                            case SAVED -> counters.saved++;
                            case DUPLICATE -> counters.duplicate++;
                            case FILTERED -> counters.filtered++;
                        }
                        if (outcome.type() != PolicyCandidateIngestService.ResultType.SAVED) {
                            messages.add(source.getName() + " / " + link.url() + "：" + outcome.message());
                        }
                    } catch (Exception e) {
                        counters.failed++;
                        messages.add(source.getName() + " / " + link.url() + "：处理失败：" + rootMessage(e));
                    }
                }
            } catch (Exception e) {
                counters.failed++;
                sourceError = rootMessage(e);
                sourceFailures.add(new SearchTaskMemoryContext.SourceFailure(
                        source.getId(), source.getName(), sourceError));
                messages.add(source.getName() + "：信源采集失败：" + sourceError);
            }

            executedSources.add(source.getId());
            SearchTaskMemoryContext snapshot = snapshot(
                    task, counters, executedSources, candidatePolicyIds, sourceFailures);
            final String failure = sourceError;
            if (failure == null) {
                safeMemory(task.getId(), "recordSourceSearched",
                        () -> memoryService.recordSourceSearched(
                                task.getId(), 1, source.getId(), source.getName(), snapshot));
            } else {
                safeMemory(task.getId(), "recordSourceFailed",
                        () -> memoryService.recordSourceFailed(
                                task.getId(), 1, source.getId(), source.getName(), snapshot, failure));
            }
        }

        SearchTaskMemoryContext finalSnapshot = snapshot(
                task, counters, executedSources, candidatePolicyIds, sourceFailures);
        safeMemory(task.getId(), "recordRoundCompleted",
                () -> memoryService.recordRoundCompleted(task.getId(), finalSnapshot));

        SearchTask finished;
        if (successfulSources == 0) {
            String message = messages.isEmpty() ? "所有信源均采集失败" : String.join("；", messages);
            finished = searchTaskService.fail(task.getId(), counters.found, counters.failed, message);
            safeMemory(task.getId(), "markFailed", () -> memoryService.markFailed(
                    task.getId(), finalSnapshot, message, "检查固定信源可用性后重新运行搜索任务"));
        } else {
            finished = searchTaskService.complete(
                    task.getId(),
                    counters.found,
                    counters.saved,
                    counters.duplicate,
                    counters.filtered,
                    counters.failed
            );
            safeMemory(task.getId(), "markCompleted",
                    () -> memoryService.markCompleted(task.getId(), finalSnapshot));
        }

        return new SearchTaskRunResult(
                finished.getId(),
                finished.getStatus(),
                counters.found,
                counters.saved,
                counters.duplicate,
                counters.filtered,
                counters.failed,
                counters.associated,
                List.copyOf(messages)
        );
    }

    private SearchTaskMemoryContext snapshot(
            SearchTask task,
            MutableCounters counters,
            List<String> executedSources,
            List<Long> candidatePolicyIds,
            List<SearchTaskMemoryContext.SourceFailure> sourceFailures
    ) {
        return memoryService.buildContext(
                task,
                executedSources,
                candidatePolicyIds,
                SearchTaskMemoryContext.Counts.of(
                        counters.found,
                        counters.saved,
                        counters.duplicate,
                        counters.filtered,
                        counters.failed
                ),
                sourceFailures
        );
    }

    private void safeMemory(Long taskId, String operation, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Agent Memory 记录失败（不影响搜索主流程）: taskId={}, operation={}, error={}",
                    taskId, operation, rootMessage(e));
        }
    }

    private List<PolicySourceProperties.Item> resolveSources(List<String> sourceIds) {
        Map<String, PolicySourceProperties.Item> configured = new LinkedHashMap<>();
        sourceProperties.getItems().forEach(source -> configured.put(source.getId(), source));
        List<String> unknown = sourceIds.stream().filter(id -> !configured.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("未知固定信源：" + unknown);
        }
        return sourceIds.stream().map(configured::get).toList();
    }

    private PolicySiteAdapter findAdapter(String sourceUrl) {
        return siteAdapters.stream()
                .filter(adapter -> adapter.supports(sourceUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有适配该政府网站的采集器：" + sourceUrl));
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null || keywords.stream().noneMatch(this::hasText)) {
            return DEFAULT_KEYWORDS;
        }
        return keywords.stream().filter(this::hasText).map(String::trim).distinct().toList();
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

    private static final class MutableCounters {
        private int found;
        private int saved;
        private int duplicate;
        private int filtered;
        private int failed;
        private int associated;
    }
}
