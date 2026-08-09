package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.PolicySelectionRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyReviewSelectionServiceTests {

    @Test
    void shouldMarkOnlyCheckedPoliciesAsApproved() {
        PolicyDocumentRepository repository = mock(PolicyDocumentRepository.class);
        PolicyReviewService service = new PolicyReviewService(repository);
        PolicyDocument first = policy(1L);
        PolicyDocument second = policy(2L);
        PolicyDocument third = policy(3L);
        when(repository.findByDailyTaskIdOrderByPublishDateDescCreatedAtDesc(7L))
                .thenReturn(List.of(first, second, third));

        service.updateSelection(7L, new PolicySelectionRequest(List.of(1L, 3L), "reviewer"));

        assertThat(first.getReviewStatus()).isEqualTo(PolicyReviewService.APPROVED);
        assertThat(second.getReviewStatus()).isEqualTo(PolicyReviewService.PENDING_REVIEW);
        assertThat(third.getReviewStatus()).isEqualTo(PolicyReviewService.APPROVED);
        verify(repository).saveAll(List.of(first, second, third));
    }

    private PolicyDocument policy(Long id) {
        PolicyDocument document = new PolicyDocument();
        document.setId(id);
        return document;
    }
}
