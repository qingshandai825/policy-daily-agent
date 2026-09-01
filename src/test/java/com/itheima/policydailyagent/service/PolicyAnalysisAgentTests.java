package com.itheima.policydailyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.domain.report.ReportSection;
import com.itheima.policydailyagent.dto.PolicyAnalysisDraft;
import com.itheima.policydailyagent.entity.PolicyDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PolicyAnalysisAgentTests {

    @Test
    void shouldUseTemplateGuideAndFallbackWhenModelReturnsInvalidSection() {
        AgentAvailabilityService availabilityService = mock(AgentAvailabilityService.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(availabilityService.requireChatModel()).thenReturn(chatModel);
        when(chatModel.call(anyString())).thenReturn("""
                \u0060\u0060\u0060json
                {
                  "basicInfo": {
                    "policyName": "人工智能赋能制造业政策",
                    "issuingAuthority": "工业和信息化部",
                    "publishDate": "2026-08-01",
                    "policyBackground": "围绕人工智能赋能新型工业化作出部署。"
                  },
                  "coreContent": "部署重点任务。",
                  "relevantContent": "推进工业大模型和高质量数据集建设。",
                  "recommendedSectionCode": "UNKNOWN",
                  "recommendationReason": "模型给出的理由",
                  "generatedTitle": "工业和信息化部部署人工智能赋能制造业工作",
                  "generatedContent": "2026年8月1日，工业和信息化部围绕人工智能赋能制造业作出部署，提出推进工业大模型和高质量数据集建设等重点任务。",
                  "evidence": ["推进工业大模型", "建设高质量数据集"]
                }
                \u0060\u0060\u0060
                """);

        PolicyAnalysisAgent agent = new PolicyAnalysisAgent(
                availabilityService,
                new ObjectMapper()
        );
        PolicyDocument policy = new PolicyDocument();
        policy.setTitle("人工智能赋能制造业政策");
        policy.setSourceName("工业和信息化部");
        policy.setPublishDate(LocalDate.of(2026, 8, 1));
        policy.setSourceUrl("https://example.gov.cn/policy");
        policy.setCleanedContent("政策提出推进工业大模型，建设高质量数据集。");

        ReportSection section = new ReportSection();
        section.setSectionCode("NATIONAL");
        section.setSectionName("（一）国家重点事项");
        section.setWritingGuide("采用时间、主体、核心部署和重点任务结构。");
        section.setContentPlaceholder("{{SECTION_NATIONAL}}");

        PolicyAnalysisDraft result = agent.analyze(
                policy,
                List.of(section),
                new PolicySectionRecommendationService.SectionRecommendation(
                        "NATIONAL",
                        "规则初判为国家层面"
                )
        );

        assertThat(result.recommendedSectionCode()).isEqualTo("NATIONAL");
        assertThat(result.recommendationReason()).contains("已采用规则初判");
        assertThat(result.generatedContent()).contains("工业大模型");
        assertThat(result.evidence()).hasSize(2);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue())
                .contains("采用时间、主体、核心部署和重点任务结构")
                .contains("政策提出推进工业大模型");
    }
}
