package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.config.PolicySourceProperties;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskStatus;
import com.itheima.policydailyagent.dto.SearchTaskRunRequest;
import com.itheima.policydailyagent.service.SearchTaskService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PolicySearchOrchestratorTests {

    @Test
    void shouldRunMultipleSourcesInOneSearchTask() {
        PolicySourceProperties properties = new PolicySourceProperties();
        properties.setItems(List.of(
                source("gov", "中国政府网", "https://www.gov.cn/zhengce/zuixin/"),
                source("miit", "工业和信息化部", "https://www.miit.gov.cn/zwgk/zcwj/")
        ));
        PolicySiteAdapter adapter = mock(PolicySiteAdapter.class);
        when(adapter.supports(anyString())).thenReturn(true);
        when(adapter.discover(anyString(), anyList(), eq(5), anyBoolean())).thenAnswer(invocation -> {
            String sourceUrl = invocation.getArgument(0);
            return List.of(new PolicySiteAdapter.DiscoveredPolicyLink(
                    "人工智能政策",
                    sourceUrl + "art/2026/policy.html",
                    "政策片段"
            ));
        });
        PolicyCandidateIngestService ingestService = mock(PolicyCandidateIngestService.class);
        when(ingestService.ingest(any(), any(), any(), anyInt()))
                .thenReturn(new PolicyCandidateIngestService.IngestOutcome(
                        PolicyCandidateIngestService.ResultType.SAVED,
                        10L,
                        true,
                        "已入库"
                ));
        SearchTaskService taskService = mock(SearchTaskService.class);
        SearchTask running = new SearchTask();
        running.setId(1L);
        running.setStatus(SearchTaskStatus.RUNNING);
        when(taskService.createAndStart(any(SearchTaskRunRequest.class), anyList(), anyList()))
                .thenReturn(running);
        SearchTask completed = new SearchTask();
        completed.setId(1L);
        completed.setStatus(SearchTaskStatus.COMPLETED);
        when(taskService.complete(eq(1L), eq(2), eq(2), eq(0), eq(0), eq(0)))
                .thenReturn(completed);

        PolicySearchOrchestrator orchestrator = new PolicySearchOrchestrator(
                properties,
                List.of(adapter),
                ingestService,
                taskService
        );
        var result = orchestrator.run(new SearchTaskRunRequest(
                "八月检索",
                "2026-08",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of("人工智能"),
                List.of("gov", "miit"),
                5,
                true
        ));

        assertThat(result.status()).isEqualTo(SearchTaskStatus.COMPLETED);
        assertThat(result.foundCount()).isEqualTo(2);
        assertThat(result.associatedCount()).isEqualTo(2);
        verify(adapter, times(2)).discover(anyString(), anyList(), eq(5), anyBoolean());
        verify(ingestService, times(2)).ingest(eq(running), any(), any(), anyInt());
    }

    private PolicySourceProperties.Item source(String id, String name, String url) {
        PolicySourceProperties.Item item = new PolicySourceProperties.Item();
        item.setId(id);
        item.setName(name);
        item.setUrl(url);
        return item;
    }
}
