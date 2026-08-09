package com.itheima.policydailyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.agent.dto.MonthlyReportContent;
import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyReportContentServiceTests {

    private final PolicyDocumentRepository repository = mock(PolicyDocumentRepository.class);
    private final ChatModel chatModel = mock(ChatModel.class);
    private final MonthlyReportContentService service = new MonthlyReportContentService(
            repository,
            chatModel,
            new ObjectMapper()
    );

    @Test
    void shouldGenerateStructuredContentFromApprovedPoliciesInSelectedMonth() {
        PolicyDocument policy = new PolicyDocument();
        policy.setTitle("工业和信息化部部署人工智能赋能制造业工作");
        policy.setSourceName("工业和信息化部");
        policy.setPublishDate(LocalDate.of(2026, 8, 8));
        policy.setSourceUrl("https://www.miit.gov.cn/example.html");
        policy.setSummary("部署行业大模型和工业智能体应用。");

        when(repository.findByPublishDateBetweenAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                PolicyReviewService.APPROVED
        )).thenReturn(List.of(policy));
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                {
                  "national":{"keyPoints":["部署人工智能赋能制造业"],"items":[{"title":"国家部署","body":"工业和信息化部部署行业大模型和工业智能体应用。"}]},
                  "provincial":{"keyPoints":[],"items":[]},
                  "pioneer":{"keyPoints":[],"overview":"材料未明确","metrics":"材料未明确","problems":"材料未明确","practices":"材料未明确"},
                  "cases":{"keyPoints":[],"items":[]},
                  "trends":{"keyPoints":["工业智能体加快落地"],"items":[{"title":"工业智能体","body":"政策部署显示工业智能体应用正在加快。"}]}
                }
                """);

        MonthlyReportContent content = service.generate(request());

        assertThat(content.reportYear()).isEqualTo(2026);
        assertThat(content.reportMonth()).isEqualTo("2026年8月");
        assertThat(content.national().items()).hasSize(1);
        assertThat(content.national().items().get(0).title()).isEqualTo("国家部署");
        assertThat(content.trends().keyPoints()).containsExactly("工业智能体加快落地");
        verify(repository).findByPublishDateBetweenAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                PolicyReviewService.APPROVED
        );
    }

    @Test
    void shouldGenerateFromApprovedPoliciesInSelectedTaskRegardlessOfPublishMonth() {
        PolicyDocument policy = new PolicyDocument();
        policy.setTitle("山东发布人工智能赋能制造业政策");
        policy.setSourceName("山东省工业和信息化厅");
        policy.setPublishDate(LocalDate.of(2026, 7, 20));
        policy.setSourceUrl("https://gxt.shandong.gov.cn/example.html");
        policy.setSummary("推动人工智能技术在制造业重点场景应用。");

        when(repository.findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                77L,
                PolicyReviewService.APPROVED
        )).thenReturn(List.of(policy));
        when(chatModel.call(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                {
                  "national":{"keyPoints":[],"items":[]},
                  "provincial":{"keyPoints":["山东推进人工智能赋能制造业"],"items":[{"title":"山东部署重点场景应用","body":"山东省推动人工智能技术在制造业重点场景应用。"}]},
                  "pioneer":{"keyPoints":[],"overview":"材料未明确","metrics":"材料未明确","problems":"材料未明确","practices":"材料未明确"},
                  "cases":{"keyPoints":[],"items":[]},
                  "trends":{"keyPoints":[],"items":[]}
                }
                """);

        MonthlyReportContent content = service.generate(new MonthlyReportGenerateRequest(
                2026,
                8,
                12,
                "2026-08",
                "2026-08-31",
                "",
                "",
                "",
                77L
        ));

        assertThat(content.reportMonth()).isEqualTo("2026年8月");
        assertThat(content.statDate()).isEqualTo("2026年8月31日");
        assertThat(content.provincial().items().get(0).title()).isEqualTo("山东部署重点场景应用");
        verify(repository).findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                77L,
                PolicyReviewService.APPROVED
        );
    }

    @Test
    void shouldCreateSafeEmptyContentWithoutCallingModelWhenNoPoliciesAreApproved() {
        when(repository.findByPublishDateBetweenAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                PolicyReviewService.APPROVED
        )).thenReturn(List.of());

        MonthlyReportContent content = service.generate(request());

        assertThat(content.national().items().get(0).body()).contains("暂无国家重点事项");
        verify(chatModel, never()).call(org.mockito.ArgumentMatchers.anyString());
    }

    private MonthlyReportGenerateRequest request() {
        return new MonthlyReportGenerateRequest(
                2026,
                8,
                12,
                "2026年8月",
                "2026年8月31日",
                "省有关领导",
                "各市工业和信息化局",
                "张三 0531-12345678"
        );
    }
}
