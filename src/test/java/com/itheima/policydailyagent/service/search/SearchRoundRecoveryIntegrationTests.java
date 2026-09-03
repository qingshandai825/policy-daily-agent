package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskPolicy;
import com.itheima.policydailyagent.domain.search.SearchTaskRound;
import com.itheima.policydailyagent.domain.search.SearchTaskRoundStatus;
import com.itheima.policydailyagent.domain.search.SearchTaskSourceFailure;
import com.itheima.policydailyagent.domain.search.SearchTaskStatus;
import com.itheima.policydailyagent.dto.SearchTaskRunRequest;
import com.itheima.policydailyagent.dto.SearchTaskRunResult;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.SearchTaskPolicyRepository;
import com.itheima.policydailyagent.repository.SearchTaskRepository;
import com.itheima.policydailyagent.repository.SearchTaskRoundRepository;
import com.itheima.policydailyagent.repository.SearchTaskSourceFailureRepository;
import com.itheima.policydailyagent.service.PolicyCrawlerService;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 多轮搜索恢复专项的集成测试：真实 {@link SearchRoundService} / 编排器 / 采集入库 /
 * 仓储 / 规划器 / 覆盖度评估 / 停止策略，仅用可控假适配器替换外部 HTTP 信源采集，
 * 用假爬虫阻断真实抓取，从而离线验证「两轮执行 → 失败信源排除 → 中断恢复」整条链路。
 */
@SpringBootTest(properties = {
        "policy.search.multi-round.enabled=true",
        "policy.search.multi-round.topics=人工智能,算力",
        "policy.search.multi-round.max-rounds=3",
        "policy.search.multi-round.no-growth-rounds=2",
        "policy.search.multi-round.coverage-threshold=1"
})
@ActiveProfiles("test")
class SearchRoundRecoveryIntegrationTests {

    @Autowired
    private PolicySearchOrchestrator orchestrator;

    @Autowired
    private SearchRoundService searchRoundService;

    @Autowired
    private SearchTaskMemoryAssembler assembler;

    @Autowired
    private SearchTaskRepository searchTaskRepository;

    @Autowired
    private SearchTaskRoundRepository roundRepository;

    @Autowired
    private SearchTaskPolicyRepository associationRepository;

    @Autowired
    private SearchTaskSourceFailureRepository sourceFailureRepository;

    @Autowired
    private PolicyDocumentRepository documentRepository;

    @MockBean
    private PolicySiteAdapter siteAdapter;

    @MockBean
    private PolicyCrawlerService crawlerService;

    private final List<String> discoveredUrls = new ArrayList<>();
    private final List<Integer> observedMaxLinks = new ArrayList<>();
    private final List<Boolean> observedFilterByKeyword = new ArrayList<>();
    private final List<List<String>> observedKeywords = new ArrayList<>();

    private String aiUrl;
    private String suanliUrl;
    private Long aiDocId;
    private Long suanliDocId;

    @BeforeEach
    void setUp() {
        aiUrl = "https://www.gov.cn/zhengce/zuixin/ai-" + UUID.randomUUID() + ".html";
        suanliUrl = "https://www.gov.cn/zhengce/zuixin/suanli-" + UUID.randomUUID() + ".html";
        aiDocId = seedDocument("人工智能产业政策", aiUrl, "人工智能相关政策正文");
        suanliDocId = seedDocument("算力产业政策", suanliUrl, "算力相关政策正文");

        discoveredUrls.clear();
        observedMaxLinks.clear();
        observedFilterByKeyword.clear();
        observedKeywords.clear();

        when(siteAdapter.supports(anyString())).thenReturn(true);
        when(siteAdapter.discover(anyString(), anyList(), anyInt(), anyBoolean())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            @SuppressWarnings("unchecked")
            List<String> keywords = inv.getArgument(1);
            int maxLinks = inv.getArgument(2);
            boolean filter = inv.getArgument(3);
            discoveredUrls.add(url);
            observedMaxLinks.add(maxLinks);
            observedFilterByKeyword.add(filter);
            observedKeywords.add(new ArrayList<>(keywords));
            if (url.contains("miit")) {
                throw new RuntimeException("TLS handshake failed");
            }
            String keyword = keywords.isEmpty() ? "人工智能" : keywords.get(0);
            String linkUrl = keyword.contains("算力") ? suanliUrl : aiUrl;
            return List.of(new PolicySiteAdapter.DiscoveredPolicyLink(
                    keyword + "产业政策", linkUrl, "snippet"));
        });

        // 阻断任何真实 HTTP 抓取：本测试全部走预置候选，绝不允许访问外网。
        when(crawlerService.crawl(anyString()))
                .thenThrow(new RuntimeException("测试不应触发真实 HTTP 抓取"));
    }

    @Test
    void multiRoundExcludesFailedSourceAndStopsOnFullCoverage() {
        SearchTaskRunResult result = orchestrator.run(new SearchTaskRunRequest(
                "恢复专项-多轮",
                "2026-08",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of("人工智能"),
                List.of("gov-latest", "miit-policy"),
                5,
                false,
                true
        ));

        assertThat(result.roundCount()).isEqualTo(2);
        assertThat(result.foundCount()).isEqualTo(2);
        assertThat(result.associatedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(SearchTaskStatus.PARTIAL_FAILED);

        // 失败信源已持久化，且第二轮目标信源不再包含失败信源。
        List<SearchTaskSourceFailure> failures =
                sourceFailureRepository.findBySearchTaskIdOrderByFirstRoundNoAsc(result.taskId());
        assertThat(failures).extracting(SearchTaskSourceFailure::getSourceId)
                .containsExactly("miit-policy");

        List<SearchTaskRound> rounds =
                roundRepository.findBySearchTaskIdOrderByRoundNoAsc(result.taskId());
        assertThat(rounds).hasSize(2);
        assertThat(rounds.get(1).getTargetSourcesJson()).isEqualTo("[\"gov-latest\"]");
        assertThat(rounds.get(1).getStopReason()).isEqualTo("ALL_TOPICS_COVERED");

        // 失败信源仅在第一轮被调用一次；正常信源被调用两轮。
        assertThat(discoveredUrls.stream().filter(u -> u.contains("miit")).count()).isEqualTo(1);
        assertThat(discoveredUrls.stream().filter(u -> !u.contains("miit")).count()).isEqualTo(2);
    }

    @Test
    void resumeRebuildsHistoryAndContinuesExcludingFailedSource() {
        // 模拟崩溃中断：任务 RUNNING 且租约已过期，第一轮已完成但 miit 信源失败。
        SearchTask task = new SearchTask();
        task.setTaskName("恢复专项-中断");
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能");
        task.setSourceIds("gov-latest,miit-policy");
        task.setStatus(SearchTaskStatus.RUNNING);
        task.setExecutorId("dead-executor");
        task.setLeaseExpiresAt(LocalDateTime.now().minusHours(1));
        MultiRoundConfig config = new MultiRoundConfig(true, 3, 2, 1, 5, 2000, List.of("人工智能", "算力"));
        task.setRunParamsJson(assembler.toJson(SearchRunParams.of(5, false, true, config)));
        task = searchTaskRepository.saveAndFlush(task);
        Long taskId = task.getId();

        SearchTaskRound round1 = new SearchTaskRound();
        round1.setSearchTaskId(taskId);
        round1.setRoundNo(1);
        round1.setStatus(SearchTaskRoundStatus.COMPLETED);
        round1.setKeywordsJson("[\"人工智能\"]");
        round1.setTargetSourcesJson("[\"gov-latest\",\"miit-policy\"]");
        round1.setFoundCount(1);
        round1.setSavedCount(1);
        round1.setFailedCount(1);
        round1.setNewAssociationCount(1);
        roundRepository.saveAndFlush(round1);

        SearchTaskSourceFailure failure = new SearchTaskSourceFailure();
        failure.setSearchTaskId(taskId);
        failure.setSourceId("miit-policy");
        failure.setSourceName("工业和信息化部-政策文件");
        failure.setMessage("TLS handshake failed");
        failure.setFirstRoundNo(1);
        sourceFailureRepository.saveAndFlush(failure);

        SearchTaskPolicy association = new SearchTaskPolicy();
        association.setSearchTaskId(taskId);
        association.setPolicyId(aiDocId);
        association.setSourceId("gov-latest");
        association.setProvider("FIXED_SOURCE");
        association.setDiscoveredUrl(aiUrl);
        association.setDiscoveredTitle("人工智能产业政策");
        association.setDiscoveryOrder(1);
        associationRepository.saveAndFlush(association);

        SearchTaskRunResult result = searchRoundService.resume(taskId);

        // 历史累计被完整重建（第一轮发现 1 + 第二轮发现 1），候选 ID 去重后为 2。
        assertThat(result.roundCount()).isEqualTo(2);
        assertThat(result.foundCount()).isEqualTo(2);
        assertThat(result.associatedCount()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(SearchTaskStatus.PARTIAL_FAILED);

        // 恢复后依然排除失败信源：miit 从未被再次调用，仅 gov 执行第二轮。
        assertThat(discoveredUrls.stream().filter(u -> u.contains("miit")).count()).isZero();
        assertThat(discoveredUrls.stream().filter(u -> !u.contains("miit")).count()).isEqualTo(1);

        // 原始执行参数被恢复使用（maxLinks=5、filterByKeyword=false），而非当前默认值。
        assertThat(observedMaxLinks).containsOnly(5);
        assertThat(observedFilterByKeyword).containsOnly(false);

        List<SearchTaskRound> rounds = roundRepository.findBySearchTaskIdOrderByRoundNoAsc(taskId);
        assertThat(rounds).hasSize(2);
        assertThat(rounds.get(1).getTargetSourcesJson()).isEqualTo("[\"gov-latest\"]");
    }

    @Test
    void concurrentResumeOnlyOneAdvances() throws Exception {
        Long taskId = seedInterruptedTask();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicReference<Throwable> loserError = new AtomicReference<>();

        Runnable attempt = () -> {
            ready.countDown();
            try {
                start.await();
                searchRoundService.resume(taskId);
                success.incrementAndGet();
            } catch (Throwable t) {
                loserError.compareAndSet(null, t);
            }
        };

        Thread t1 = new Thread(attempt);
        Thread t2 = new Thread(attempt);
        t1.start();
        t2.start();
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        t1.join(30_000);
        t2.join(30_000);

        assertThat(success.get()).isEqualTo(1);
        assertThat(loserError.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lateResumeCannotReclaimAfterCompletion() {
        Long taskId = seedInterruptedTask();

        SearchTaskRunResult result = searchRoundService.resume(taskId);
        assertThat(result.status()).isIn(SearchTaskStatus.COMPLETED, SearchTaskStatus.PARTIAL_FAILED);

        assertThatThrownBy(() -> searchRoundService.resume(taskId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已正常完成");
    }

    @Test
    void resumeRetriesInterruptedRoundWithOriginalKeywords() {
        // 模拟中断：round 1 处于 RUNNING（已计划但未完成），关键词为用户自定义的"量子计算"
        // （不在主题词典 人工智能/算力 内），且 miit 信源已失败并被检查点持久化。
        SearchTask task = new SearchTask();
        task.setTaskName("恢复专项-中断重试");
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("量子计算");
        task.setSourceIds("gov-latest,miit-policy");
        task.setStatus(SearchTaskStatus.RUNNING);
        task.setExecutorId("dead-executor");
        task.setLeaseExpiresAt(LocalDateTime.now().minusHours(1));
        MultiRoundConfig config = new MultiRoundConfig(true, 3, 2, 1, 5, 2000, List.of("人工智能", "算力"));
        task.setRunParamsJson(assembler.toJson(SearchRunParams.of(5, false, true, config)));
        task = searchTaskRepository.saveAndFlush(task);
        Long taskId = task.getId();

        SearchTaskRound round1 = new SearchTaskRound();
        round1.setSearchTaskId(taskId);
        round1.setRoundNo(1);
        round1.setRetryNo(0);
        round1.setStatus(SearchTaskRoundStatus.RUNNING);
        round1.setKeywordsJson("[\"量子计算\"]");
        round1.setTargetSourcesJson("[\"gov-latest\",\"miit-policy\"]");
        roundRepository.saveAndFlush(round1);

        SearchTaskSourceFailure failure = new SearchTaskSourceFailure();
        failure.setSearchTaskId(taskId);
        failure.setSourceId("miit-policy");
        failure.setSourceName("工业和信息化部-政策文件");
        failure.setMessage("TLS handshake failed");
        failure.setFirstRoundNo(1);
        sourceFailureRepository.saveAndFlush(failure);

        searchRoundService.resume(taskId);

        // 中断轮次被原地重试（round_no 仍为 1，retry_no=1），而非跳到 round_no=2 丢弃原计划。
        List<SearchTaskRound> rounds = roundRepository.findBySearchTaskIdOrderByRoundNoAsc(taskId);
        assertThat(rounds).anyMatch(r -> r.getRoundNo() == 1 && r.getRetryNo() == 1);

        // 原始自定义关键词被保留："量子计算" 不在主题词典，重新规划只会得到"算力/人工智能"。
        assertThat(observedKeywords).anyMatch(kw -> kw.contains("量子计算"));

        // 失败信源依然被排除。
        assertThat(discoveredUrls.stream().filter(u -> u.contains("miit")).count()).isZero();
    }

    @Test
    void resumeRefusesWhenRetryLimitExceeded() {
        SearchTask task = new SearchTask();
        task.setTaskName("恢复专项-重试上限");
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能");
        task.setSourceIds("gov-latest");
        task.setStatus(SearchTaskStatus.RUNNING);
        task.setExecutorId("dead-executor");
        task.setLeaseExpiresAt(LocalDateTime.now().minusHours(1));
        MultiRoundConfig config = new MultiRoundConfig(true, 3, 2, 1, 5, 2000, List.of("人工智能", "算力"));
        task.setRunParamsJson(assembler.toJson(SearchRunParams.of(5, false, true, config)));
        task = searchTaskRepository.saveAndFlush(task);
        Long taskId = task.getId();

        SearchTaskRound round1 = new SearchTaskRound();
        round1.setSearchTaskId(taskId);
        round1.setRoundNo(1);
        round1.setRetryNo(3);  // 已重试 3 次，超过上限 SearchRoundService.MAX_ROUND_RETRIES=3
        round1.setStatus(SearchTaskRoundStatus.RUNNING);
        round1.setKeywordsJson("[\"人工智能\"]");
        round1.setTargetSourcesJson("[\"gov-latest\"]");
        roundRepository.saveAndFlush(round1);

        SearchTaskRunResult result = searchRoundService.resume(taskId);

        assertThat(result.status()).isEqualTo(SearchTaskStatus.FAILED);
        SearchTask reloaded = searchTaskRepository.findById(taskId).orElseThrow();
        assertThat(reloaded.getTerminationReason()).isEqualTo("RETRY_LIMIT_EXCEEDED");
    }

    private Long seedInterruptedTask() {
        SearchTask task = new SearchTask();
        task.setTaskName("恢复专项-中断");
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能");
        task.setSourceIds("gov-latest,miit-policy");
        task.setStatus(SearchTaskStatus.RUNNING);
        task.setExecutorId("dead-executor");
        task.setLeaseExpiresAt(LocalDateTime.now().minusHours(1));
        MultiRoundConfig config = new MultiRoundConfig(true, 3, 2, 1, 5, 2000, List.of("人工智能", "算力"));
        task.setRunParamsJson(assembler.toJson(SearchRunParams.of(5, false, true, config)));
        task = searchTaskRepository.saveAndFlush(task);
        Long taskId = task.getId();

        SearchTaskRound round1 = new SearchTaskRound();
        round1.setSearchTaskId(taskId);
        round1.setRoundNo(1);
        round1.setRetryNo(0);
        round1.setStatus(SearchTaskRoundStatus.COMPLETED);
        round1.setKeywordsJson("[\"人工智能\"]");
        round1.setTargetSourcesJson("[\"gov-latest\",\"miit-policy\"]");
        round1.setFoundCount(1);
        round1.setSavedCount(1);
        round1.setFailedCount(1);
        round1.setNewAssociationCount(1);
        roundRepository.saveAndFlush(round1);

        SearchTaskSourceFailure failure = new SearchTaskSourceFailure();
        failure.setSearchTaskId(taskId);
        failure.setSourceId("miit-policy");
        failure.setSourceName("工业和信息化部-政策文件");
        failure.setMessage("TLS handshake failed");
        failure.setFirstRoundNo(1);
        sourceFailureRepository.saveAndFlush(failure);

        SearchTaskPolicy association = new SearchTaskPolicy();
        association.setSearchTaskId(taskId);
        association.setPolicyId(aiDocId);
        association.setSourceId("gov-latest");
        association.setProvider("FIXED_SOURCE");
        association.setDiscoveredUrl(aiUrl);
        association.setDiscoveredTitle("人工智能产业政策");
        association.setDiscoveryOrder(1);
        associationRepository.saveAndFlush(association);

        return taskId;
    }

    private Long seedDocument(String title, String url, String content) {
        PolicyDocument document = new PolicyDocument();
        document.setTitle(title);
        document.setSourceUrl(url);
        document.setSourceName("中国政府网");
        document.setContent(content);
        document.setReviewStatus(PolicyReviewStatus.PENDING);
        return documentRepository.saveAndFlush(document).getId();
    }
}
