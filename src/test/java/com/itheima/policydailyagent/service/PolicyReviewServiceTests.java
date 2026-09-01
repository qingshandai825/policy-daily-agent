package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.*;
import com.itheima.policydailyagent.dto.PolicyReviewRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.PolicyReviewRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PolicyReviewServiceTests {

    private final PolicyDocumentRepository policyRepository = mock(PolicyDocumentRepository.class);
    private final PolicyReviewRepository reviewRepository = mock(PolicyReviewRepository.class);
    private final PolicyReviewService service = new PolicyReviewService(
            policyRepository,
            reviewRepository,
            new PolicyReviewStateMachine()
    );

    @Test
    void shouldAppendHistoryAndOpenAnalysisGateWhenAccepted() {
        PolicyDocument policy = new PolicyDocument();
        policy.setId(7L);
        policy.setReviewStatus(PolicyReviewStatus.PENDING);
        policy.setAnalysisStatus(PolicyAnalysisStatus.NOT_ANALYZED);

        when(policyRepository.findById(7L)).thenReturn(Optional.of(policy));
        when(policyRepository.save(policy)).thenReturn(policy);

        PolicyDocument result = service.accept(
                7L,
                new PolicyReviewRequest("reviewer", "纳入本月月报候选")
        );

        assertThat(result.getReviewStatus()).isEqualTo(PolicyReviewStatus.ACCEPTED);
        assertThat(result.getAnalysisStatus()).isEqualTo(PolicyAnalysisStatus.READY);

        ArgumentCaptor<PolicyReview> captor = ArgumentCaptor.forClass(PolicyReview.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousStatus()).isEqualTo(PolicyReviewStatus.PENDING);
        assertThat(captor.getValue().getReviewStatus()).isEqualTo(PolicyReviewStatus.ACCEPTED);
        assertThat(captor.getValue().getReviewedBy()).isEqualTo("reviewer");
    }
}
