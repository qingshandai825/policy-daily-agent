package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.config.PolicySourceProperties;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskPolicy;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.SearchTaskPolicyRepository;
import com.itheima.policydailyagent.service.PolicyCrawlerService;
import com.itheima.policydailyagent.service.PolicyDateFilterService;
import com.itheima.policydailyagent.service.PolicyDocumentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PolicyCandidateIngestServiceTests {

    @Test
    void shouldAssociateSamePolicyWithDifferentSearchTasks() {
        PolicyDocumentRepository documentRepository = mock(PolicyDocumentRepository.class);
        SearchTaskPolicyRepository associationRepository = mock(SearchTaskPolicyRepository.class);
        PolicyDocument existing = new PolicyDocument();
        existing.setId(99L);
        existing.setTitle("人工智能政策");
        existing.setSourceUrl("https://www.gov.cn/zhengce/2026/policy.html");
        when(documentRepository.findBySourceUrl(existing.getSourceUrl())).thenReturn(Optional.of(existing));
        when(associationRepository.existsBySearchTaskIdAndPolicyId(anyLong(), eq(99L))).thenReturn(false);

        PolicyCrawlerService crawlerService = mock(PolicyCrawlerService.class);
        PolicyCandidateIngestService service = new PolicyCandidateIngestService(
                crawlerService,
                mock(PolicyDateFilterService.class),
                mock(PolicyDocumentService.class),
                documentRepository,
                associationRepository
        );
        PolicySourceProperties.Item source = new PolicySourceProperties.Item();
        source.setId("gov-latest");
        source.setName("中国政府网");
        var link = new PolicySiteAdapter.DiscoveredPolicyLink(
                existing.getTitle(),
                existing.getSourceUrl(),
                "政策搜索片段"
        );
        SearchTask firstTask = new SearchTask();
        firstTask.setId(1L);
        SearchTask secondTask = new SearchTask();
        secondTask.setId(2L);

        var first = service.ingest(firstTask, source, link, 1);
        var second = service.ingest(secondTask, source, link, 1);

        assertThat(first.type()).isEqualTo(PolicyCandidateIngestService.ResultType.DUPLICATE);
        assertThat(second.type()).isEqualTo(PolicyCandidateIngestService.ResultType.DUPLICATE);
        ArgumentCaptor<SearchTaskPolicy> captor = ArgumentCaptor.forClass(SearchTaskPolicy.class);
        verify(associationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SearchTaskPolicy::getSearchTaskId)
                .containsExactly(1L, 2L);
        verifyNoInteractions(crawlerService);
    }
}
