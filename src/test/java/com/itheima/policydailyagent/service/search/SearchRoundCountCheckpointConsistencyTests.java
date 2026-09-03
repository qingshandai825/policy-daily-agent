package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskPolicy;
import com.itheima.policydailyagent.domain.search.SearchTaskRound;
import com.itheima.policydailyagent.domain.search.SearchTaskRoundStatus;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缺陷2（中断重试后的统计及检查点一致性）回归测试。
 *
 * <p>场景 A：第一次尝试已保存候选并建立关联后崩溃，重试再次遇到同一候选 —— 第一次的
 * saved 不被抹掉、重试不重复计数为 duplicate，saved/duplicate/associated 从业务数据重建。
 * <p>场景 B：信源 TLS 失败且检查点写入故障 —— 任务不谎称检查点已保存，进入可识别
 * CHECKPOINT_FAILED 中断态，而非吞掉异常继续。
 */
@SpringBootTest(properties = {
        "policy.search.multi-round.enabled=true",
        "policy.search.multi-round.topics=人工智能,算力",
        "policy.search.multi-round.max-rounds=3",
        "policy.search.multi-round.no-growth-rounds=2",
        "policy.search.multi-round.coverage-threshold=1"
})
@ActiveProfiles("test")
class SearchRoundCountCheckpointConsistencyTests {

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
    private PolicyDocumentRepository documentRepository;

    @MockBean
    private PolicySiteAdapter siteAdapter;

    @MockBean
    private PolicyCrawlerService crawlerService;

    @MockBean
    private SearchTaskSourceFailureRepository sourceFailureRepository;

    @BeforeEach
    void setUp() {
        when(siteAdapter.supports(anyString())).thenReturn(true);
        when(crawlerService.crawl(anyString()))
                .thenThrow(new RuntimeException("测试不应触发真实 HTTP 抓取"));
        when(sourceFailureRepository.findBySearchTaskIdOrderByFirstRoundNoAsc(anyLong()))
                .thenReturn(List.of());
    }

    @Test
    void resumeReconstructsSavedAfterInterruptedRetry() {
        String pUrl = "https://www.gov.cn/zhengce/zuixin/p-" + UUID.randomUUID() + ".html";
        MultiRoundConfig config = new MultiRoundConfig(true, 1, 2, 1, 5, 2000, List.of("人工智能", "算力"));

        SearchTask task = new SearchTask();
        task.setTaskName("一致性-中断重试");
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能");
        task.setSourceIds("gov-latest");
        task.setStatus(SearchTaskStatus.RUNNING);
        task.setExecutorId("dead-executor");
        task.setLeaseExpiresAt(LocalDateTime.now().minusHours(1));
        task.setRunParamsJson(assembler.toJson(SearchRunParams.of(5, false, true, config)));
        task = searchTaskRepository.saveAndFlush(task);
        Long taskId = task.getId();

        // 第一次尝试已保存候选 P（searchTaskId=taskId 是「由本任务创建」的持久证据）。
        Long pId = seedSavedDocument(taskId, "人工智能产业政策", pUrl, "人工智能相关政策正文");
        // 第一次尝试已建立关联。
        SearchTaskPolicy association = new SearchTaskPolicy();
        association.setSearchTaskId(taskId);
        association.setPolicyId(pId);
        association.setSourceId("gov-latest");
        association.setProvider("FIXED_SOURCE");
        association.setDiscoveredUrl(pUrl);
        association.setDiscoveredTitle("人工智能产业政策");
        association.setDiscoveryOrder(1);
        associationRepository.saveAndFlush(association);

        // 第一次尝试在候选提交后、轮次结果落库前崩溃：round 1 仍停留在 RUNNING。
        SearchTaskRound round1 = new SearchTaskRound();
        round1.setSearchTaskId(taskId);
        round1.setRoundNo(1);
        round1.setRetryNo(0);
        round1.setStatus(SearchTaskRoundStatus.RUNNING);
        round1.setKeywordsJson("[\"人工智能\"]");
        round1.setTargetSourcesJson("[\"gov-latest\"]");
        roundRepository.saveAndFlush(round1);

        // 重试再次遇到 P（同一 URL）→ 物理上为 duplicate，但重建后不得抹掉第一次的 saved。
        when(siteAdapter.discover(anyString(), anyList(), anyInt(), anyBoolean()))
                .thenReturn(List.of(new PolicySiteAdapter.DiscoveredPolicyLink(
                        "人工智能产业政策", pUrl, "snippet")));

        SearchTaskRunResult result = searchRoundService.resume(taskId);

        assertThat(result.status()).isIn(SearchTaskStatus.COMPLETED, SearchTaskStatus.PARTIAL_FAILED);
        assertThat(result.savedCount()).isEqualTo(1);     // 第一次的 saved 保留
        assertThat(result.duplicateCount()).isZero();     // 重试不重复计数
        assertThat(result.associatedCount()).isEqualTo(1); // 任务唯一候选数为 1
    }

    @Test
    void sourceFailureCheckpointWriteFailureMarksInterrupted() {
        when(siteAdapter.discover(anyString(), anyList(), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("TLS handshake failed"));
        when(sourceFailureRepository.existsBySearchTaskIdAndSourceId(anyLong(), anyString()))
                .thenReturn(false);
        when(sourceFailureRepository.save(any()))
                .thenThrow(new RuntimeException("checkpoint db down"));

        SearchTaskRunResult result = orchestrator.run(new SearchTaskRunRequest(
                "一致性-检查点故障",
                "2026-08",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of("人工智能"),
                List.of("miit-policy"),
                5,
                false,
                true
        ));

        assertThat(result.status()).isEqualTo(SearchTaskStatus.FAILED);
        assertThat(result.countsIncomplete()).isTrue();
        SearchTask reloaded = searchTaskRepository.findById(result.taskId()).orElseThrow();
        assertThat(reloaded.getTerminationReason()).isEqualTo("CHECKPOINT_FAILED");

        // 检查点写入确实被尝试，但失败未被吞掉（任务未谎称检查点已保存）。
        verify(sourceFailureRepository).save(any());
    }

    private Long seedSavedDocument(Long taskId, String title, String url, String content) {
        PolicyDocument document = new PolicyDocument();
        document.setTitle(title);
        document.setSourceUrl(url);
        document.setSourceName("中国政府网");
        document.setContent(content);
        document.setReviewStatus(PolicyReviewStatus.PENDING);
        document.setSearchTaskId(taskId);
        return documentRepository.saveAndFlush(document).getId();
    }
}
