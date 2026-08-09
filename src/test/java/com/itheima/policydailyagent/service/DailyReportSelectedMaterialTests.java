package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.agent.dto.DailyReportSynthesis;
import com.itheima.policydailyagent.entity.DailyTask;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.DailyTaskRepository;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyReportSelectedMaterialTests {

    @Test
    void shouldWriteAgentSynthesisAndAllowHistoricalPolicyInOpenDateWorkspace() throws Exception {
        PolicyDocumentRepository policyRepository = mock(PolicyDocumentRepository.class);
        DailyTaskRepository taskRepository = mock(DailyTaskRepository.class);
        DailyReportService service = new DailyReportService(policyRepository, taskRepository);
        DailyTask task = new DailyTask();
        task.setTaskName("政策素材工作区");
        PolicyDocument policy = new PolicyDocument();
        policy.setTitle("历史政策也可由人工选择");
        policy.setPublishDate(LocalDate.of(2024, 1, 2));
        policy.setSourceUrl("https://example.gov.cn/history.html");
        policy.setEvidenceSnippet("历史政策原文证据");
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));
        when(policyRepository.findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                7L,
                PolicyReviewService.APPROVED
        )).thenReturn(List.of(policy));

        byte[] report = service.generateDailyReport(new DailyReportSynthesis(
                7L,
                "Agent 对多条材料形成综合研判。",
                List.of("人工智能政策协同推进")
        ));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(report))) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .collect(Collectors.joining("\n"));
            assertThat(text)
                    .contains("综合研判")
                    .contains("Agent 对多条材料形成综合研判")
                    .contains("人工智能政策协同推进")
                    .contains("历史政策也可由人工选择")
                    .contains("当前栏目页（未限制发布日期）");
        }
    }
}
