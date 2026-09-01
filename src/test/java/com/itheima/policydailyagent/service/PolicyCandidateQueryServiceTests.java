package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.PolicyAnalysisStatus;
import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.domain.search.SearchTaskPolicy;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.SearchTaskPolicyRepository;
import com.itheima.policydailyagent.repository.SearchTaskRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PolicyCandidateQueryServiceTests {

    @Test
    void shouldFilterCandidateAndOnlyExposeFullContentInDetail() {
        SearchTaskRepository taskRepository = mock(SearchTaskRepository.class);
        SearchTaskPolicyRepository associationRepository = mock(SearchTaskPolicyRepository.class);
        PolicyDocumentRepository documentRepository = mock(PolicyDocumentRepository.class);
        when(taskRepository.existsById(1L)).thenReturn(true);

        SearchTaskPolicy association = new SearchTaskPolicy();
        association.setSearchTaskId(1L);
        association.setPolicyId(10L);
        association.setSourceId("miit-policy");
        association.setProvider("FIXED_SOURCE");
        association.setDiscoveredUrl("https://www.miit.gov.cn/policy.html");
        association.setSearchSnippet("人工智能赋能制造业政策片段");
        when(associationRepository.findBySearchTaskIdOrderByDiscoveryOrderAscIdAsc(1L))
                .thenReturn(List.of(association));
        when(associationRepository.findBySearchTaskIdAndPolicyId(1L, 10L))
                .thenReturn(Optional.of(association));

        PolicyDocument document = new PolicyDocument();
        document.setId(10L);
        document.setTitle("人工智能赋能制造业通知");
        document.setSourceName("工业和信息化部");
        document.setSourceUrl("https://www.miit.gov.cn/policy.html");
        document.setCleanedContent("完整政策正文");
        document.setPolicyType("通知");
        document.setKeywords("人工智能,智能制造");
        document.setRelevanceScore(BigDecimal.valueOf(85));
        document.setReviewStatus(PolicyReviewStatus.PENDING);
        document.setAnalysisStatus(PolicyAnalysisStatus.NOT_ANALYZED);
        when(documentRepository.findAllById(List.of(10L))).thenReturn(List.of(document));
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));

        PolicyCandidateQueryService service = new PolicyCandidateQueryService(
                taskRepository,
                associationRepository,
                documentRepository
        );

        var list = service.list(1L, PolicyReviewStatus.PENDING, "人工智能", "miit-policy");
        var detail = service.get(1L, 10L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).content()).isNull();
        assertThat(list.get(0).shortSummary()).contains("人工智能");
        assertThat(detail.content()).isEqualTo("完整政策正文");
    }
}
