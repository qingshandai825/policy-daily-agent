package com.itheima.policydailyagent.agent;

import com.itheima.policydailyagent.agent.dto.MonthlyPioneerContent;
import com.itheima.policydailyagent.agent.dto.MonthlyReportContent;
import com.itheima.policydailyagent.agent.dto.MonthlyReportSection;
import com.itheima.policydailyagent.agent.dto.ReviewGateResult;
import com.itheima.policydailyagent.agent.entity.AgentRun;
import com.itheima.policydailyagent.agent.model.AgentStage;
import com.itheima.policydailyagent.agent.service.AgentMemoryService;
import com.itheima.policydailyagent.agent.service.AgentToolExecutor;
import com.itheima.policydailyagent.agent.tool.AgentToolCall;
import com.itheima.policydailyagent.agent.tool.AgentToolRegistry;
import com.itheima.policydailyagent.agent.tool.impl.MonthlyReportContentAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.MonthlyReportTemplateAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.ReviewGateAgentTool;
import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import com.itheima.policydailyagent.dto.ReviewTaskSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyReportOrchestratorTests {

    @Test
    void shouldGenerateContentThenFillTemplateThroughTwoAgentTools() {
        AgentMemoryService memory = mock(AgentMemoryService.class);
        AgentToolExecutor executor = mock(AgentToolExecutor.class);
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        PolicyDailyOrchestrator orchestrator = new PolicyDailyOrchestrator(memory, executor, registry);
        AgentRun run = mock(AgentRun.class);
        MonthlyReportContentAgentTool contentTool = mock(MonthlyReportContentAgentTool.class);
        MonthlyReportTemplateAgentTool templateTool = mock(MonthlyReportTemplateAgentTool.class);
        MonthlyReportGenerateRequest request = new MonthlyReportGenerateRequest(
                2026, 8, 12, "2026年8月", "2026年8月31日", "", "", ""
        );
        MonthlyReportContent content = content();
        byte[] report = new byte[]{1, 2, 3, 4};

        when(run.getId()).thenReturn(21L);
        when(memory.startRun(request)).thenReturn(run);
        when(registry.require(MonthlyReportContentAgentTool.NAME, MonthlyReportContentAgentTool.class))
                .thenReturn(contentTool);
        when(registry.require(MonthlyReportTemplateAgentTool.NAME, MonthlyReportTemplateAgentTool.class))
                .thenReturn(templateTool);
        when(executor.execute(any(AgentToolCall.class), eq(contentTool), eq(request))).thenReturn(content);
        when(executor.execute(any(AgentToolCall.class), eq(templateTool), eq(content))).thenReturn(report);

        assertThat(orchestrator.generateMonthlyReport(request)).isEqualTo(report);
        verify(memory).markRunning(21L, AgentStage.SUMMARIZATION);
        verify(memory).markGeneratingReport(21L);
        verify(memory).markCompleted(21L, 4);
    }

    @Test
    void shouldReviewSelectedTaskThenGenerateContentAndFillOriginalTemplate() {
        AgentMemoryService memory = mock(AgentMemoryService.class);
        AgentToolExecutor executor = mock(AgentToolExecutor.class);
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        PolicyDailyOrchestrator orchestrator = new PolicyDailyOrchestrator(memory, executor, registry);
        AgentRun run = mock(AgentRun.class);
        ReviewGateAgentTool reviewTool = mock(ReviewGateAgentTool.class);
        MonthlyReportContentAgentTool contentTool = mock(MonthlyReportContentAgentTool.class);
        MonthlyReportTemplateAgentTool templateTool = mock(MonthlyReportTemplateAgentTool.class);
        MonthlyReportGenerateRequest request = new MonthlyReportGenerateRequest(
                2026, 8, 12, "2026-08", "2026-08-31", "", "", "", 101L
        );
        ReviewGateResult gate = new ReviewGateResult(
                true,
                "已选择政策素材，可以生成月报",
                new ReviewTaskSummary(101L, 3, 1, 2, 0, 0)
        );
        MonthlyReportContent content = content();
        byte[] report = new byte[]{1, 2, 3, 4};

        when(run.getId()).thenReturn(22L);
        when(memory.ensureRunForTask(101L)).thenReturn(run);
        when(registry.require(ReviewGateAgentTool.NAME, ReviewGateAgentTool.class)).thenReturn(reviewTool);
        when(registry.require(MonthlyReportContentAgentTool.NAME, MonthlyReportContentAgentTool.class))
                .thenReturn(contentTool);
        when(registry.require(MonthlyReportTemplateAgentTool.NAME, MonthlyReportTemplateAgentTool.class))
                .thenReturn(templateTool);
        when(executor.execute(any(AgentToolCall.class), eq(reviewTool), eq(101L))).thenReturn(gate);
        when(executor.execute(any(AgentToolCall.class), eq(contentTool), eq(request))).thenReturn(content);
        when(executor.execute(any(AgentToolCall.class), eq(templateTool), eq(content))).thenReturn(report);

        assertThat(orchestrator.generateMonthlyReport(request)).isEqualTo(report);
        verify(memory).recordReviewGate(22L, gate);
        verify(memory).markRunning(22L, AgentStage.SUMMARIZATION);
        verify(memory).markGeneratingReport(22L);
        verify(memory).markCompleted(22L, 4);
    }

    private MonthlyReportContent content() {
        MonthlyReportSection empty = new MonthlyReportSection(List.of("材料未明确"), List.of());
        return new MonthlyReportContent(
                2026, 8, 12, "2026年8月", "2026年8月31日", "", "", "",
                empty,
                empty,
                new MonthlyPioneerContent(List.of("材料未明确"), "", "", "", ""),
                empty,
                empty
        );
    }
}
