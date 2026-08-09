package com.itheima.policydailyagent.agent;

import com.itheima.policydailyagent.agent.dto.AgentCollectionOutcome;
import com.itheima.policydailyagent.agent.dto.AgentRunDetails;
import com.itheima.policydailyagent.agent.dto.DailyReportSynthesis;
import com.itheima.policydailyagent.agent.dto.MonthlyReportContent;
import com.itheima.policydailyagent.agent.dto.PolicyDiscoveryToolInput;
import com.itheima.policydailyagent.agent.dto.ReviewGateResult;
import com.itheima.policydailyagent.agent.entity.AgentRun;
import com.itheima.policydailyagent.agent.exception.ReviewNotReadyException;
import com.itheima.policydailyagent.agent.model.AgentStage;
import com.itheima.policydailyagent.agent.service.AgentMemoryService;
import com.itheima.policydailyagent.agent.service.AgentToolExecutor;
import com.itheima.policydailyagent.agent.tool.AgentToolCall;
import com.itheima.policydailyagent.agent.tool.AgentToolDescriptor;
import com.itheima.policydailyagent.agent.tool.AgentToolRegistry;
import com.itheima.policydailyagent.agent.tool.impl.DailyReportAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.DailyReportSynthesisAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.MonthlyReportContentAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.MonthlyReportTemplateAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.PolicyDiscoveryAgentTool;
import com.itheima.policydailyagent.agent.tool.impl.ReviewGateAgentTool;
import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import com.itheima.policydailyagent.dto.PolicyDiscoverRequest;
import com.itheima.policydailyagent.dto.PolicyDiscoverResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PolicyDailyOrchestrator {

    private final AgentMemoryService memoryService;
    private final AgentToolExecutor toolExecutor;
    private final AgentToolRegistry toolRegistry;

    public PolicyDailyOrchestrator(
            AgentMemoryService memoryService,
            AgentToolExecutor toolExecutor,
            AgentToolRegistry toolRegistry
    ) {
        this.memoryService = memoryService;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
    }

    public AgentCollectionOutcome collectPolicies(PolicyDiscoverRequest request) {
        AgentRun run = memoryService.startRun(request);
        memoryService.markRunning(run.getId(), AgentStage.DISCOVERY);

        try {
            PolicyDiscoveryAgentTool tool = toolRegistry.require(
                    PolicyDiscoveryAgentTool.NAME,
                    PolicyDiscoveryAgentTool.class
            );
            PolicyDiscoverResult result = toolExecutor.execute(
                    new AgentToolCall(run.getId(), null, AgentStage.DISCOVERY),
                    tool,
                    new PolicyDiscoveryToolInput(run.getId(), request)
            );
            memoryService.waitForReview(run.getId(), result);
            return new AgentCollectionOutcome(run.getId(), result);
        } catch (RuntimeException error) {
            memoryService.markFailed(run.getId(), AgentStage.DISCOVERY, error);
            throw error;
        }
    }

    public ReviewGateResult resumeAfterReview(Long taskId) {
        AgentRun run = memoryService.ensureRunForTask(taskId);
        try {
            ReviewGateResult gate = executeReviewGate(run, taskId);
            memoryService.recordReviewGate(run.getId(), gate);
            return gate;
        } catch (RuntimeException error) {
            memoryService.markFailed(run.getId(), AgentStage.REVIEW_GATE, error);
            throw error;
        }
    }

    public byte[] generateDailyReport(Long taskId) {
        AgentRun run = memoryService.ensureRunForTask(taskId);
        AgentStage stage = AgentStage.REVIEW_GATE;

        try {
            ReviewGateResult gate = executeReviewGate(run, taskId);
            memoryService.recordReviewGate(run.getId(), gate);
            if (!gate.ready()) {
                throw new ReviewNotReadyException(gate.reason());
            }

            stage = AgentStage.SUMMARIZATION;
            memoryService.markRunning(run.getId(), stage);
            DailyReportSynthesisAgentTool synthesisTool = toolRegistry.require(
                    DailyReportSynthesisAgentTool.NAME,
                    DailyReportSynthesisAgentTool.class
            );
            DailyReportSynthesis synthesis = toolExecutor.execute(
                    new AgentToolCall(run.getId(), taskId, stage),
                    synthesisTool,
                    taskId
            );

            stage = AgentStage.REPORT_GENERATION;
            memoryService.markGeneratingReport(run.getId());
            DailyReportAgentTool reportTool = toolRegistry.require(
                    DailyReportAgentTool.NAME,
                    DailyReportAgentTool.class
            );
            byte[] report = toolExecutor.execute(
                    new AgentToolCall(run.getId(), taskId, stage),
                    reportTool,
                    synthesis
            );
            memoryService.markCompleted(run.getId(), report.length);
            return report;
        } catch (ReviewNotReadyException error) {
            throw error;
        } catch (RuntimeException error) {
            memoryService.markFailed(run.getId(), stage, error);
            throw error;
        }
    }

    public byte[] generateMonthlyReport(MonthlyReportGenerateRequest request) {
        Long taskId = request == null ? null : request.taskId();
        AgentRun run = taskId == null
                ? memoryService.startRun(request)
                : memoryService.ensureRunForTask(taskId);
        AgentStage stage = taskId == null ? AgentStage.SUMMARIZATION : AgentStage.REVIEW_GATE;

        try {
            if (taskId != null) {
                ReviewGateResult gate = executeReviewGate(run, taskId);
                memoryService.recordReviewGate(run.getId(), gate);
                if (!gate.ready()) {
                    throw new ReviewNotReadyException(gate.reason());
                }
                stage = AgentStage.SUMMARIZATION;
            }
            memoryService.markRunning(run.getId(), stage);

            MonthlyReportContentAgentTool contentTool = toolRegistry.require(
                    MonthlyReportContentAgentTool.NAME,
                    MonthlyReportContentAgentTool.class
            );
            MonthlyReportContent content = toolExecutor.execute(
                    new AgentToolCall(run.getId(), taskId, stage),
                    contentTool,
                    request
            );

            stage = AgentStage.REPORT_GENERATION;
            memoryService.markGeneratingReport(run.getId());
            MonthlyReportTemplateAgentTool templateTool = toolRegistry.require(
                    MonthlyReportTemplateAgentTool.NAME,
                    MonthlyReportTemplateAgentTool.class
            );
            byte[] report = toolExecutor.execute(
                    new AgentToolCall(run.getId(), taskId, stage),
                    templateTool,
                    content
            );
            memoryService.markCompleted(run.getId(), report.length);
            return report;
        } catch (ReviewNotReadyException error) {
            throw error;
        } catch (RuntimeException error) {
            memoryService.markFailed(run.getId(), stage, error);
            throw error;
        }
    }
    public AgentRunDetails getRun(Long runId) {
        return memoryService.getDetails(runId);
    }

    public AgentRunDetails getLatestRunForTask(Long taskId) {
        return memoryService.getLatestDetailsForTask(taskId);
    }

    public List<AgentRun> listRecentRuns() {
        return memoryService.listRecentRuns();
    }

    public List<AgentToolDescriptor> listTools() {
        return toolRegistry.descriptors();
    }

    private ReviewGateResult executeReviewGate(AgentRun run, Long taskId) {
        ReviewGateAgentTool reviewTool = toolRegistry.require(
                ReviewGateAgentTool.NAME,
                ReviewGateAgentTool.class
        );
        return toolExecutor.execute(
                new AgentToolCall(run.getId(), taskId, AgentStage.REVIEW_GATE),
                reviewTool,
                taskId
        );
    }
}
