package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.PolicyAnalysisStatus;
import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PolicySummaryServiceTests {

    private final ChatModel chatModel = mock(ChatModel.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
    private final PolicyDocumentRepository repository = mock(PolicyDocumentRepository.class);
    private PolicySummaryService service;

    @BeforeEach
    void setUp() {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        service = new PolicySummaryService(chatModelProvider, repository);
    }

    @Test
    void shouldRejectAnalysisBeforeHumanAcceptance() {
        PolicyDocument policy = policy(PolicyReviewStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.summarizeById(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACCEPTED");

        verifyNoInteractions(chatModel);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldAnalyzeAcceptedPolicy() {
        PolicyDocument policy = policy(PolicyReviewStatus.ACCEPTED);
        when(repository.findById(1L)).thenReturn(Optional.of(policy));
        when(chatModel.call(anyString())).thenReturn("结构化分析");
        when(repository.save(policy)).thenReturn(policy);

        PolicyDocument result = service.summarizeById(1L);

        assertThat(result.getSummary()).isEqualTo("结构化分析");
        assertThat(result.getAnalysisStatus()).isEqualTo(PolicyAnalysisStatus.ANALYZED);
        verify(repository).save(policy);
    }

    private PolicyDocument policy(PolicyReviewStatus status) {
        PolicyDocument policy = new PolicyDocument();
        policy.setId(1L);
        policy.setTitle("政策标题");
        policy.setSourceName("工业和信息化部");
        policy.setContent("政策正文");
        policy.setReviewStatus(status);
        return policy;
    }
}
