package com.itheima.policydailyagent.agent;

import com.itheima.policydailyagent.agent.dto.AgentCollectionOutcome;
import com.itheima.policydailyagent.agent.dto.DailyReportSynthesis;
import com.itheima.policydailyagent.agent.dto.PolicyDiscoveryToolInput;
import com.itheima.policydailyagent.agent.dto.ReviewGateResult;
import com.itheima.policydailyagent.agent.entity.AgentRun;
import com.itheima.policydailyagent.agent.exception.ReviewNotReadyException;
import com.itheima.policydailyagent.agent.model.AgentStage;
import com.itheima.policydailyagent.agent.service.AgentMemoryService;
import com.itheima.policydailyagent.agent.service.AgentToolExecutor;
import com.itheima.policydailyagent.agent.tool.AgentToolCall;
import com.itheima.policydailyagent.agent.tool.AgentToolRegistry;
import com.itheima.policydailyagent.agent.tool.impl.DailyReportAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.DailyReportSynthesisAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.PolicyDiscoveryAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.ReviewGateAgentTool;
import com.itheima.policydailyagent.dto.PolicyDiscoverRequest;
import com.itheima.policydailyagent.dto.PolicyDiscoverResult;
import com.itheima.policydailyagent.dto.ReviewTaskSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyDailyOrchestratorTests {

    private final AgentMemoryService memoryService = mock(AgentMemoryService.class);
    private final AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
    private final AgentToolRegistry toolRegistry = mock(AgentToolRegistry.class);
    private final PolicyDailyOrchestrator orchestrator = new PolicyDailyOrchestrator(
            memoryService,
            toolExecutor,
            toolRegistry
    );

    @Test
    void shouldCollectPoliciesAndPauseForHumanReview() {
        AgentRun run = run(7L);
        PolicyDiscoveryAgentTool discoveryTool = mock(PolicyDiscoveryAgentTool.class);
        PolicyDiscoverRequest request = request();
        PolicyDiscoverResult result = new PolicyDiscoverResult(
                101L, 5, 3, 1, 1, 0, 0,
                List.of("政策一"), List.of(), List.of(), List.of()
        );

        when(memoryService.startRun(request)).thenReturn(run);
        when(toolRegistry.require(PolicyDiscoveryAgentTool.NAME, PolicyDiscoveryAgentTool.class))
                .thenReturn(discoveryTool);
        when(toolExecutor.execute(
                any(AgentToolCall.class),
                eq(discoveryTool),
                any(PolicyDiscoveryToolInput.class)
        )).thenReturn(result);

        AgentCollectionOutcome outcome = orchestrator.collectPolicies(request);

        assertThat(outcome.agentRunId()).isEqualTo(7L);
        assertThat(outcome.result().taskId()).isEqualTo(101L);
        verify(memoryService).markRunning(7L, AgentStage.DISCOVERY);
        verify(memoryService).waitForReview(7L, result);
    }

    @Test
    void shouldBlockReportWhenNoMaterialIsSelected() {
        AgentRun run = run(8L);
        ReviewGateAgentTool reviewTool = mock(ReviewGateAgentTool.class);
        ReviewGateResult gate = new ReviewGateResult(
                false,
                "请至少勾选一条政策素材",
                new ReviewTaskSummary(101L, 3, 3, 0, 0, 0)
        );

        when(memoryService.ensureRunForTask(101L)).thenReturn(run);
        when(toolRegistry.require(ReviewGateAgentTool.NAME, ReviewGateAgentTool.class))
                .thenReturn(reviewTool);
        when(toolExecutor.execute(any(AgentToolCall.class), eq(reviewTool), eq(101L)))
                .thenReturn(gate);

        assertThatThrownBy(() -> orchestrator.generateDailyReport(101L))
                .isInstanceOf(ReviewNotReadyException.class)
                .hasMessageContaining("勾选");

        verify(memoryService).recordReviewGate(8L, gate);
        verify(memoryService, never()).markGeneratingReport(any());
        verify(toolRegistry, never()).require(DailyReportAgentTool.NAME, DailyReportAgentTool.class);
    }

    @Test
    void shouldSynthesizeSelectedMaterialsBeforeGeneratingReport() {
        AgentRun run = run(9L);
        ReviewGateAgentTool reviewTool = mock(ReviewGateAgentTool.class);
        DailyReportSynthesisAgentTool synthesisTool = mock(DailyReportSynthesisAgentTool.class);
        DailyReportAgentTool reportTool = mock(DailyReportAgentTool.class);
        ReviewGateResult gate = new ReviewGateResult(
                true,
                "已选择政策素材，可以生成日报",
                new ReviewTaskSummary(101L, 3, 1, 2, 0, 0)
        );
        DailyReportSynthesis synthesis = new DailyReportSynthesis(
                101L,
                "多项政策共同推动人工智能赋能制造业。",
                List.of("政策支持力度持续增强")
        );
        byte[] report = new byte[]{1, 2, 3};

        when(memoryService.ensureRunForTask(101L)).thenReturn(run);
        when(toolRegistry.require(ReviewGateAgentTool.NAME, ReviewGateAgentTool.class))
                .thenReturn(reviewTool);
        when(toolRegistry.require(DailyReportSynthesisAgentTool.NAME, DailyReportSynthesisAgentTool.class))
                .thenReturn(synthesisTool);
        when(toolRegistry.require(DailyReportAgentTool.NAME, DailyReportAgentTool.class))
                .thenReturn(reportTool);
        when(toolExecutor.execute(any(AgentToolCall.class), eq(reviewTool), eq(101L)))
                .thenReturn(gate);
        when(toolExecutor.execute(any(AgentToolCall.class), eq(synthesisTool), eq(101L)))
                .thenReturn(synthesis);
        when(toolExecutor.execute(any(AgentToolCall.class), eq(reportTool), eq(synthesis)))
                .thenReturn(report);

        assertThat(orchestrator.generateDailyReport(101L)).isEqualTo(report);
        verify(memoryService).markRunning(9L, AgentStage.SUMMARIZATION);
        verify(memoryService).markGeneratingReport(9L);
        verify(memoryService).markCompleted(9L, 3);
    }

    private AgentRun run(Long id) {
        AgentRun run = mock(AgentRun.class);
        when(run.getId()).thenReturn(id);
        return run;
    }

    private PolicyDiscoverRequest request() {
        return new PolicyDiscoverRequest(
                "https://example.gov.cn/policies",
                List.of("人工智能"),
                10,
                false,
                LocalDate.of(2026, 8, 9),
                LocalDate.of(2026, 8, 9),
                "政策日报"
        );
    }
}