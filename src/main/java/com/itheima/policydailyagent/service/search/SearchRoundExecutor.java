package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.config.PolicySourceProperties;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskSourceFailure;
import com.itheima.policydailyagent.repository.SearchTaskSourceFailureRepository;
import com.itheima.policydailyagent.service.SearchTaskService;
import com.itheima.policydailyagent.service.memory.AgentTaskMemoryService;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单轮信源执行器：按给定关键词与信源集合跑一遍采集循环，逐个信源入库候选政策，
 * 并记录 SOURCE_SEARCHED / SOURCE_FAILED 事件。单轮与多轮流程共用此执行器，
 * 避免采集循环逻辑重复堆叠在编排器或多轮服务中。
 */
@Component
public class SearchRoundExecutor {

    private static final Logger log = LoggerFactory.getLogger(SearchRoundExecutor.class);

    private final PolicySourceProperties sourceProperties;
    private final List<PolicySiteAdapter> siteAdapters;
    private final PolicyCandidateIngestService ingestService;
    private final AgentTaskMemoryService memoryService;
    private final SearchTaskSourceFailureRepository sourceFailureRepository;
    private final SearchTaskService searchTaskService;

    public SearchRoundExecutor(
            PolicySourceProperties sourceProperties,
            List<PolicySiteAdapter> siteAdapters,
            PolicyCandidateIngestService ingestService,
            AgentTaskMemoryService memoryService,
            SearchTaskSourceFailureRepository sourceFailureRepository,
            SearchTaskService searchTaskService
    ) {
        this.sourceProperties = sourceProperties;
        this.siteAdapters = siteAdapters;
        this.ingestService = ingestService;
        this.memoryService = memoryService;
        this.sourceFailureRepository = sourceFailureRepository;
        this.searchTaskService = searchTaskService;
    }

    /**
     * 校验并解析信源 ID 为配置项。未知信源直接抛出，供编排器在创建任务前快速失败。
     */
    public List<PolicySourceProperties.Item> resolveSources(List<String> sourceIds) {
        Map<String, PolicySourceProperties.Item> configured = new LinkedHashMap<>();
        sourceProperties.getItems().forEach(source -> configured.put(source.getId(), source));
        List<String> unknown = sourceIds.stream().filter(id -> !configured.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("未知固定信源：" + unknown);
        }
        return sourceIds.stream().map(configured::get).toList();
    }

    public RoundExecution execute(
            SearchTask task,
            List<String> keywords,
            List<PolicySourceProperties.Item> sources,
            int maxLinks,
            boolean filterByKeyword,
            int roundNo,
            String executorId
    ) {
        MutableCounters counters = new MutableCounters();
        List<String> messages = new ArrayList<>();
        List<String> executedSources = new ArrayList<>();
        List<Long> candidatePolicyIds = new ArrayList<>();
        List<SearchTaskMemoryContext.SourceFailure> sourceFailures = new ArrayList<>();
        int successfulSources = 0;
        int discoveryOrder = 0;

        for (PolicySourceProperties.Item source : sources) {
            String sourceError = null;
            try {
                PolicySiteAdapter adapter = findAdapter(source.getUrl());
                List<PolicySiteAdapter.DiscoveredPolicyLink> links = adapter.discover(
                        source.getUrl(), keywords, maxLinks, filterByKeyword);
                successfulSources++;
                counters.found += links.size();
                if (links.isEmpty()) {
                    messages.add(source.getName() + "：未发现匹配链接");
                }
                for (PolicySiteAdapter.DiscoveredPolicyLink link : links) {
                    discoveryOrder++;
                    try {
                        PolicyCandidateIngestService.IngestOutcome outcome = ingestService.ingest(
                                task, source, link, discoveryOrder);
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
                // 信源级失败在信源边界立即持久化（检查点），整轮中途崩溃也不丢失该失败记录，
                // 恢复后据此排除该信源，不重访。多轮模式下该写入受执行权保护，且失败不再被吞掉。
                persistSourceFailure(task.getId(), roundNo, executorId, source.getId(), source.getName(), sourceError);
            }

            executedSources.add(source.getId());
            SearchTaskMemoryContext snapshot = memoryService.buildContext(
                    task, executedSources, candidatePolicyIds,
                    SearchTaskMemoryContext.Counts.of(
                            counters.found, counters.saved, counters.duplicate,
                            counters.filtered, counters.failed),
                    sourceFailures);
            final String failure = sourceError;
            try {
                if (failure == null) {
                    memoryService.recordSourceSearched(
                            task.getId(), roundNo, source.getId(), source.getName(), snapshot);
                } else {
                    memoryService.recordSourceFailed(
                            task.getId(), roundNo, source.getId(), source.getName(), snapshot, failure);
                }
            } catch (Exception e) {
                log.warn("Agent Memory 记录失败（不影响搜索主流程）: taskId={}, source={}, error={}",
                        task.getId(), source.getId(), rootMessage(e));
            }
        }

        return new RoundExecution(
                counters.found, counters.saved, counters.duplicate, counters.filtered,
                counters.failed, counters.associated, successfulSources,
                List.copyOf(executedSources), List.copyOf(candidatePolicyIds),
                List.copyOf(sourceFailures), List.copyOf(messages));
    }

    private PolicySiteAdapter findAdapter(String sourceUrl) {
        return siteAdapters.stream()
                .filter(adapter -> adapter.supports(sourceUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有适配该政府网站的采集器：" + sourceUrl));
    }

    /**
     * 在信源边界持久化信源级失败检查点。幂等（同一任务同一信源只记录一次）。
     *
     * <p>多轮模式（executorId 非空）下，该写入受执行权保护：在持有任务行锁的事务内校验
     * executor_id 后写入，执行权丢失则抛 {@link LeaseLostException}（向上传播，终止旧执行器）。
     * 写入因数据库不可用失败时抛 {@link SourceFailureCheckpointException}，绝不吞掉后继续宣称
     * 检查点已保存——由多轮流程据此进入可识别中断状态，数据库恢复后可重试。
     */
    private void persistSourceFailure(
            Long taskId,
            int roundNo,
            String executorId,
            String sourceId,
            String sourceName,
            String message
    ) {
        try {
            if (executorId == null) {
                // 单轮模式：无执行权概念，仅做幂等写入，DB 不可用时抛 SourceFailureCheckpointException。
                if (sourceFailureRepository.existsBySearchTaskIdAndSourceId(taskId, sourceId)) {
                    return;
                }
                sourceFailureRepository.save(newSourceFailure(taskId, roundNo, sourceId, sourceName, message));
                return;
            }
            searchTaskService.withOwnership(taskId, executorId, () -> {
                if (sourceFailureRepository.existsBySearchTaskIdAndSourceId(taskId, sourceId)) {
                    return null;
                }
                return sourceFailureRepository.save(
                        newSourceFailure(taskId, roundNo, sourceId, sourceName, message));
            });
        } catch (LeaseLostException e) {
            throw e;
        } catch (SourceFailureCheckpointException e) {
            throw e;
        } catch (Exception e) {
            throw new SourceFailureCheckpointException(taskId, sourceId, e);
        }
    }

    private SearchTaskSourceFailure newSourceFailure(
            Long taskId, int roundNo, String sourceId, String sourceName, String message) {
        SearchTaskSourceFailure entity = new SearchTaskSourceFailure();
        entity.setSearchTaskId(taskId);
        entity.setSourceId(sourceId);
        entity.setSourceName(sourceName);
        entity.setMessage(message);
        entity.setFirstRoundNo(roundNo);
        return entity;
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
