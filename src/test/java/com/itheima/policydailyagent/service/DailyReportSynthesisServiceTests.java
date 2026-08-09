package com.itheima.policydailyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.agent.dto.DailyReportSynthesis;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyReportSynthesisServiceTests {

    @Test
    void shouldSynthesizeAllSelectedPolicyEvidence() {
        PolicyDocumentRepository repository = mock(PolicyDocumentRepository.class);
        ChatModel chatModel = mock(ChatModel.class);
        DailyReportSynthesisService service = new DailyReportSynthesisService(
                repository,
                chatModel,
                new ObjectMapper()
        );
        PolicyDocument first = policy(1L, "人工智能政策一", "支持工业智能体应用");
        PolicyDocument second = policy(2L, "智能制造政策二", "推动重点制造场景开放");
        when(repository.findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                7L,
                PolicyReviewService.APPROVED
        )).thenReturn(List.of(first, second));
        when(chatModel.call(anyString())).thenReturn("""
                {"overview":"两项政策共同推动人工智能技术与制造场景融合。","keyPoints":["工业智能体应用加快","重点场景持续开放"]}
                """);

        DailyReportSynthesis result = service.synthesize(7L);

        assertThat(result.taskId()).isEqualTo(7L);
        assertThat(result.overview()).contains("共同推动");
        assertThat(result.keyPoints()).containsExactly("工业智能体应用加快", "重点场景持续开放");
    }

    private PolicyDocument policy(Long id, String title, String content) {
        PolicyDocument document = new PolicyDocument();
        document.setId(id);
        document.setTitle(title);
        document.setSourceName("政府部门");
        document.setPublishDate(LocalDate.of(2026, 8, 9));
        document.setSourceUrl("https://example.gov.cn/" + id + ".html");
        document.setContent(content);
        return document;
    }
}
