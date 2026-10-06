package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.domain.search.*;
import com.itheima.policydailyagent.dto.SearchTaskRunRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.*;
import com.itheima.policydailyagent.service.PolicyCrawlerService;
import com.itheima.policydailyagent.service.memory.AgentTaskMemoryService;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实编排、仓储和Memory；模型、HTTP均用可控替身，绝不调用外网。 */
@SpringBootTest(properties = {
        "policy.search.multi-round.enabled=true",
        "policy.search.multi-round.semantic-feedback-enabled=true",
        "policy.search.multi-round.topics=人工智能",
        "policy.search.multi-round.coverage-threshold=2",
        "policy.search.multi-round.max-rounds=3"
})
@ActiveProfiles("test")
class SemanticSearchIntegrationTests {
    @Autowired private PolicySearchOrchestrator orchestrator;
    @Autowired private SearchRoundService service;
    @Autowired private SearchTaskMemoryAssembler assembler;
    @Autowired private AgentTaskMemoryService memory;
    @Autowired private SearchTaskRepository tasks;
    @Autowired private SearchTaskRoundRepository rounds;
    @Autowired private SearchTaskPolicyRepository associations;
    @Autowired private PolicyDocumentRepository documents;
    @Autowired private AgentTaskMemoryRepository memories;
    @Autowired private AgentTaskEventRepository events;
    @MockBean private PolicySiteAdapter adapter;
    @MockBean private PolicyCrawlerService crawler;
    @MockBean private ChatModel model;
    private PolicyDocument first;
    private PolicyDocument second;
    private final List<String> visitedSources = new ArrayList<>();
    private final List<List<String>> queries = new ArrayList<>();

    @BeforeEach
    void setup() {
        first = seed("推广人工智能技术，计划建设智能工厂。");
        second = seed("应用人工智能技术，推进智能工厂建设。");
        visitedSources.clear(); queries.clear();
        when(adapter.supports(anyString())).thenReturn(true);
        when(adapter.discover(anyString(), anyList(), anyInt(), anyBoolean())).thenAnswer(inv -> {
            String source = inv.getArgument(0);
            List<String> keywords = inv.getArgument(1);
            visitedSources.add(source); queries.add(List.copyOf(keywords));
            var doc = keywords.contains("智能工厂") ? second : first;
            return List.of(new PolicySiteAdapter.DiscoveredPolicyLink(doc.getTitle(), doc.getSourceUrl(), "官方材料"));
        });
        when(crawler.crawl(anyString())).thenThrow(new AssertionError("不允许真实HTTP"));
    }

    @Test
    void resultFeedbackChangesQueryAndActualSourceThenPersistsMemory() {
        when(model.call(anyString())).thenReturn(labels(false), labels(true));
        var result = orchestrator.run(new SearchTaskRunRequest("语义搜索", "2026-08",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), List.of("人工智能"),
                List.of("gov-latest", "miit-policy"), 5, true, true));
        assertThat(result.roundCount()).isEqualTo(2);
        assertThat(result.associatedCount()).isEqualTo(2);
        assertThat(visitedSources).hasSize(3); // 首轮两信源，第二轮只有计划选中的一个。
        assertThat(queries.get(2)).contains("智能工厂").doesNotContain("人工智能");
        var history = rounds.findBySearchTaskIdOrderByRoundNoAsc(result.taskId());
        assertThat(history.get(1).getTargetSourcesJson()).isEqualTo("[\"gov-latest\"]");
        assertThat(history.get(0).getSearchFeedbackJson()).contains("智能工厂", "SEMANTIC");
        var savedMemory = memories.findAll().stream()
                .filter(m -> result.taskId().equals(m.getBusinessTaskId())).findFirst().orElseThrow();
        var context = assembler.parseContext(savedMemory.getContextJson());
        assertThat(context.searchFeedback().coverage().get(0).status()).isEqualTo(CoverageStatus.COVERED);
        assertThat(context.executedPlans()).hasSize(2);
        assertThat(context.searchFeedback().canFinishByAssessment()).isTrue();
        assertThat(context.stopReason()).isEqualTo("LLM_MATERIAL_SUFFICIENT");
        assertThat(events.findAll()).anyMatch(e -> savedMemory.getId().equals(e.getMemoryId())
                && e.getEventType().name().equals("SEARCH_FEEDBACK_EVALUATED"));
        assertThat(documents.findById(first.getId()).orElseThrow().getReviewStatus()).isEqualTo(PolicyReviewStatus.PENDING);
        verify(model, times(2)).call(anyString());
        verifyNoInteractions(crawler);
    }

    @Test
    void resumeReusesInterruptedPlanAndSavedFeedbackWithoutRepeatingCompletedQuery() {
        SearchTask task = new SearchTask();
        task.setTaskName("恢复语义搜索"); task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1)); task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能"); task.setSourceIds("gov-latest,miit-policy");
        task.setStatus(SearchTaskStatus.RUNNING); task.setExecutorId("dead");
        task.setLeaseExpiresAt(LocalDateTime.now().minusHours(1));
        task.setRunParamsJson(assembler.toJson(SearchRunParams.of(5, true, true,
                new MultiRoundConfig(true, 3, 2, 2, 5, 2000, List.of("人工智能"), true))));
        task = tasks.saveAndFlush(task); memory.initialize(task);
        associate(task, first);
        SearchTaskRound completed = round(task, 1, SearchTaskRoundStatus.COMPLETED,
                List.of("人工智能"), List.of("gov-latest", "miit-policy"));
        completed.setNewAssociationCount(1);
        completed.setSearchFeedbackJson(assembler.toJson(new SearchFeedback("SEMANTIC", "test",
                List.of(new TopicCoverageResult("人工智能", 1, CoverageStatus.INSUFFICIENT, List.of(first.getId()))),
                List.of(new SearchFeedback.TermEvidence("人工智能", "智能工厂", first.getId(), "计划建设智能工厂")), "待补材料",
                new SearchFeedback.MaterialAssessment(false, List.of("人工智能"), List.of(first.getId()), "缺少应用进展", List.of()))));
        rounds.saveAndFlush(completed);
        rounds.saveAndFlush(round(task, 2, SearchTaskRoundStatus.RUNNING, List.of("智能工厂"), List.of("miit-policy")));
        when(model.call(anyString())).thenReturn(labels(true));
        var result = service.resume(task.getId());
        assertThat(result.roundCount()).isEqualTo(2);
        assertThat(queries).containsExactly(List.of("智能工厂"));
        assertThat(visitedSources).singleElement().asString().contains("miit");
        var attempts = rounds.findBySearchTaskIdOrderByRoundNoAsc(task.getId());
        assertThat(attempts).anyMatch(r -> r.getRoundNo() == 2 && r.getRetryNo() == 1
                && r.getStatus() == SearchTaskRoundStatus.COMPLETED);
        verify(model, times(1)).call(anyString());
    }

    private String labels(boolean complete) {
        return labels(complete, complete);
    }

    private String labels(boolean complete, boolean sufficient) {
        String evidence = "{\"topic\":\"人工智能\",\"policyId\":" + first.getId() + ",\"quote\":\"推广人工智能技术\"}";
        if (complete) evidence += ",{\"topic\":\"人工智能\",\"policyId\":" + second.getId() + ",\"quote\":\"应用人工智能技术\"}";
        return "{\"evidence\":[" + evidence + "],\"terms\":[{\"topic\":\"人工智能\",\"term\":\"智能工厂\",\"policyId\":"
                + first.getId() + ",\"quote\":\"计划建设智能工厂\"}],\"materialSufficient\":" + sufficient
                + ",\"missingTopics\":" + (sufficient ? "[]" : "[\"人工智能\"]")
                + ",\"evidencePolicyIds\":[" + first.getId() + (complete ? "," + second.getId() : "")
                + "],\"reason\":\"" + (sufficient ? "实质材料足以支持目标" : "仍缺具体应用进展") + "\"}";
    }

    @Test
    void modelCanFinishAfterFirstRoundBelowCoverageCountThreshold() {
        when(model.call(anyString())).thenReturn(labels(false, true));
        var result = orchestrator.run(new SearchTaskRunRequest("首轮充分", "2026-08",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), List.of("人工智能"),
                List.of("gov-latest", "miit-policy"), 5, true, true));
        assertThat(result.roundCount()).isEqualTo(1);
        assertThat(tasks.findById(result.taskId()).orElseThrow().getTerminationReason()).isEqualTo("LLM_MATERIAL_SUFFICIENT");
        var feedback = assembler.parseJson(rounds.findBySearchTaskIdOrderByRoundNoAsc(result.taskId()).get(0).getSearchFeedbackJson(), SearchFeedback.class);
        assertThat(feedback.coverage().get(0).status()).isEqualTo(CoverageStatus.INSUFFICIENT);
        assertThat(feedback.canFinishByAssessment()).isTrue();
        verify(model, times(1)).call(anyString());
    }

    @Test
    void coveredStatisticsDoNotOverrideModelRequestForAnotherRound() {
        doAnswer(inv -> {
            List<String> keywords = inv.getArgument(1);
            queries.add(List.copyOf(keywords));
            return List.of(new PolicySiteAdapter.DiscoveredPolicyLink(first.getTitle(), first.getSourceUrl(), "材料"),
                    new PolicySiteAdapter.DiscoveredPolicyLink(second.getTitle(), second.getSourceUrl(), "材料"));
        }).when(adapter).discover(anyString(), anyList(), anyInt(), anyBoolean());
        when(model.call(anyString())).thenReturn(labels(true, false), labels(true, true));
        var result = orchestrator.run(new SearchTaskRunRequest("覆盖但不足", "2026-08",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), List.of("人工智能"),
                List.of("gov-latest"), 5, true, true));
        assertThat(result.roundCount()).isEqualTo(2);
        var feedback = assembler.parseJson(rounds.findBySearchTaskIdOrderByRoundNoAsc(result.taskId()).get(0).getSearchFeedbackJson(), SearchFeedback.class);
        assertThat(feedback.coverage().get(0).status()).isEqualTo(CoverageStatus.COVERED);
        assertThat(feedback.materialAssessment().materialSufficient()).isFalse();
        assertThat(queries.get(1)).contains("智能工厂");
        verify(model, times(2)).call(anyString());
    }

    @Test
    void roundBudgetEndsSearchEvenWhenModelKeepsRequestingMore() {
        when(model.call(anyString())).thenReturn(labels(false, false), labels(true, false));
        var result = orchestrator.run(new SearchTaskRunRequest("模型一直要求继续", "2026-08",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), List.of("人工智能"),
                List.of("gov-latest", "miit-policy"), 5, true, true));
        assertThat(result.roundCount()).isEqualTo(3);
        assertThat(tasks.findById(result.taskId()).orElseThrow().getTerminationReason()).isEqualTo("MAX_ROUNDS_REACHED");
        verify(model, times(3)).call(anyString());
    }

    @Test
    void recoveryHonorsSavedDecisionForNewTasksAndOriginalStoppingPolicyForVersionTwo() {
        for (int version : List.of(3, 2)) {
            visitedSources.clear(); queries.clear(); clearInvocations(model);
            SearchTask task = new SearchTask();
            task.setTaskName("充分性恢复-v" + version); task.setReportMonth("2026-08");
            task.setTargetStartDate(LocalDate.of(2026, 8, 1)); task.setTargetEndDate(LocalDate.of(2026, 8, 31));
            task.setKeywords("人工智能"); task.setSourceIds("gov-latest");
            task.setStatus(SearchTaskStatus.RUNNING); task.setExecutorId("dead");
            task.setLeaseExpiresAt(LocalDateTime.now().minusHours(1));
            task.setRunParamsJson(assembler.toJson(SearchRunParams.of(5, true, true,
                    new MultiRoundConfig(true, 3, 2, 2, 5, 2000, List.of("人工智能"), true)))
                    .replace("\"version\":3", "\"version\":" + version));
            task = tasks.saveAndFlush(task); memory.initialize(task); associate(task, first);
            var completed = round(task, 1, SearchTaskRoundStatus.COMPLETED, List.of("人工智能"), List.of("gov-latest"));
            completed.setNewAssociationCount(1);
            completed.setSearchFeedbackJson(assembler.toJson(new SearchFeedback("SEMANTIC", "test",
                    List.of(new TopicCoverageResult("人工智能", 1, CoverageStatus.INSUFFICIENT, List.of(first.getId()))),
                    List.of(new SearchFeedback.TermEvidence("人工智能", "智能工厂", first.getId(), "计划建设智能工厂")), "已有充分性评估",
                    new SearchFeedback.MaterialAssessment(true, List.of(), List.of(first.getId()), "材料足以支持目标", List.of()))));
            rounds.saveAndFlush(completed);
            when(model.call(anyString())).thenReturn(labels(true));
            var result = service.resume(task.getId());
            if (version == 3) {
                assertThat(result.roundCount()).isEqualTo(1);
                assertThat(queries).isEmpty();
                assertThat(tasks.findById(task.getId()).orElseThrow().getTerminationReason()).isEqualTo("LLM_MATERIAL_SUFFICIENT");
                verify(model, never()).call(anyString());
            } else {
                assertThat(result.roundCount()).isEqualTo(2);
                assertThat(queries).singleElement().asList().contains("智能工厂");
                assertThat(tasks.findById(task.getId()).orElseThrow().getTerminationReason()).isEqualTo("ALL_TOPICS_COVERED");
                verify(model, times(1)).call(anyString());
            }
        }
    }
    private PolicyDocument seed(String body) {
        var doc = new PolicyDocument(); doc.setTitle("智能制造通知"); doc.setSourceName("中国政府网");
        doc.setSourceUrl("https://www.gov.cn/" + UUID.randomUUID() + ".html"); doc.setContent(body);
        doc.setPublishDate(LocalDate.of(2026, 8, 5)); doc.setReviewStatus(PolicyReviewStatus.PENDING);
        return documents.saveAndFlush(doc);
    }
    private void associate(SearchTask task, PolicyDocument doc) {
        var association = new SearchTaskPolicy(); association.setSearchTaskId(task.getId());
        association.setPolicyId(doc.getId()); association.setSourceId("gov-latest");
        association.setProvider("FIXED_SOURCE"); association.setDiscoveredUrl(doc.getSourceUrl());
        association.setDiscoveryOrder(1); associations.saveAndFlush(association);
    }
    private SearchTaskRound round(SearchTask task, int no, SearchTaskRoundStatus status,
            List<String> keywords, List<String> sources) {
        var round = new SearchTaskRound(); round.setSearchTaskId(task.getId()); round.setRoundNo(no);
        round.setStatus(status); round.setKeywordsJson(assembler.toJson(keywords));
        round.setTargetSourcesJson(assembler.toJson(sources)); round.setPlanJson(assembler.toJson(new RoundPlan(keywords, sources)));
        return round;
    }
}
