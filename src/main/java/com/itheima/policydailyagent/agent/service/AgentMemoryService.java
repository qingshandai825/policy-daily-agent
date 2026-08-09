package com.itheima.policydailyagent.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itheima.policydailyagent.agent.dto.AgentRunDetails;
import com.itheima.policydailyagent.agent.dto.ReviewGateResult;
import com.itheima.policydailyagent.agent.entity.AgentRun;
import com.itheima.policydailyagent.agent.entity.AgentStep;
import com.itheima.policydailyagent.agent.model.AgentRunStatus;
import com.itheima.policydailyagent.agent.model.AgentStage;
import com.itheima.policydailyagent.agent.model.AgentStepStatus;
import com.itheima.policydailyagent.agent.model.ToolFailureType;
import com.itheima.policydailyagent.agent.repository.AgentRunRepository;
import com.itheima.policydailyagent.agent.repository.AgentStepRepository;
import com.itheima.policydailyagent.agent.tool.AgentToolCall;
import com.itheima.policydailyagent.dto.PolicyDiscoverResult;
import com.itheima.policydailyagent.repository.DailyTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentMemoryService {

    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final DailyTaskRepository dailyTaskRepository;
    private final ObjectMapper objectMapper;

    public AgentMemoryService(
            AgentRunRepository agentRunRepository,
            AgentStepRepository agentStepRepository,
            DailyTaskRepository dailyTaskRepository,
            ObjectMapper objectMapper
    ) {
        this.agentRunRepository = agentRunRepository;
        this.agentStepRepository = agentStepRepository;
        this.dailyTaskRepository = dailyTaskRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentRun startRun(Object input) {
        AgentRun run = new AgentRun();
        run.setStatus(AgentRunStatus.CREATED);
        run.setInputSnapshot(writeJson(input));
        run.setMemorySnapshot("{}");
        return agentRunRepository.save(run);
    }

    @Transactional
    public AgentRun ensureRunForTask(Long taskId) {
        if (taskId == null || !dailyTaskRepository.existsById(taskId)) {
            throw new IllegalArgumentException("Daily task does not exist, id=" + taskId);
        }
        return agentRunRepository.findTopByDailyTaskIdOrderByCreatedAtDesc(taskId)
                .orElseGet(() -> {
                    AgentRun run = new AgentRun();
                    run.setDailyTaskId(taskId);
                    run.setStatus(AgentRunStatus.WAITING_REVIEW);
                    run.setCurrentStage(AgentStage.REVIEW_GATE);
                    run.setInputSnapshot("{\"source\":\"legacy-task\"}");
                    run.setMemorySnapshot("{\"nextAction\":\"HUMAN_REVIEW\"}");
                    run.setWaitingReviewAt(LocalDateTime.now());
                    return agentRunRepository.save(run);
                });
    }

    @Transactional
    public AgentRun markRunning(Long runId, AgentStage stage) {
        AgentRun run = getRun(runId);
        run.setStatus(AgentRunStatus.RUNNING);
        run.setCurrentStage(stage);
        run.setStartedAt(run.getStartedAt() == null ? LocalDateTime.now() : run.getStartedAt());
        run.setLastError(null);
        return agentRunRepository.save(run);
    }

    @Transactional
    public AgentRun waitForReview(Long runId, PolicyDiscoverResult result) {
        AgentRun run = getRun(runId);
        run.setDailyTaskId(result.taskId());
        run.setStatus(AgentRunStatus.WAITING_REVIEW);
        run.setCurrentStage(AgentStage.REVIEW_GATE);
        run.setWaitingReviewAt(LocalDateTime.now());

        ObjectNode memory = readMemory(run);
        memory.put("taskId", result.taskId());
        memory.put("foundLinks", result.foundLinks());
        memory.put("savedCount", result.savedCount());
        memory.put("duplicateCount", result.duplicateCount());
        memory.put("filteredCount", result.filteredCount());
        memory.put("failedCount", result.failedCount());
        memory.put("summarizedCount", result.summarizedCount());
        memory.put("nextAction", "HUMAN_REVIEW");
        run.setMemorySnapshot(writeJson(memory));
        return agentRunRepository.save(run);
    }

    @Transactional
    public AgentRun recordReviewGate(Long runId, ReviewGateResult gate) {
        AgentRun run = getRun(runId);
        run.setCurrentStage(AgentStage.REVIEW_GATE);
        run.setStatus(gate.ready() ? AgentRunStatus.READY_FOR_REPORT : AgentRunStatus.WAITING_REVIEW);
        run.setLastError(null);
        run.setCompletedAt(null);

        ObjectNode memory = readMemory(run);
        memory.put("reviewReady", gate.ready());
        memory.put("reviewReason", gate.reason());
        memory.put("approvedCount", gate.summary().approvedCount());
        memory.put("pendingCount", gate.summary().pendingCount());
        memory.put("rejectedCount", gate.summary().rejectedCount());
        memory.put("needsEditCount", gate.summary().needsEditCount());
        memory.put("nextAction", gate.ready() ? "GENERATE_REPORT" : "HUMAN_REVIEW");
        run.setMemorySnapshot(writeJson(memory));
        return agentRunRepository.save(run);
    }

    @Transactional
    public AgentRun markGeneratingReport(Long runId) {
        AgentRun run = getRun(runId);
        run.setStatus(AgentRunStatus.GENERATING_REPORT);
        run.setCurrentStage(AgentStage.REPORT_GENERATION);
        run.setLastError(null);
        return agentRunRepository.save(run);
    }

    @Transactional
    public AgentRun markCompleted(Long runId, int reportSizeBytes) {
        AgentRun run = getRun(runId);
        ObjectNode memory = readMemory(run);
        boolean hasPartialFailures = memory.path("failedCount").asInt(0) > 0;
        run.setStatus(hasPartialFailures ? AgentRunStatus.PARTIAL_SUCCESS : AgentRunStatus.COMPLETED);
        run.setCurrentStage(AgentStage.REPORT_GENERATION);
        run.setCompletedAt(LocalDateTime.now());
        run.setLastError(null);

        memory.put("reportSizeBytes", reportSizeBytes);
        memory.put("nextAction", "DONE");
        run.setMemorySnapshot(writeJson(memory));
        return agentRunRepository.save(run);
    }

    @Transactional
    public AgentRun markFailed(Long runId, AgentStage stage, Throwable error) {
        AgentRun run = getRun(runId);
        run.setStatus(AgentRunStatus.FAILED);
        run.setCurrentStage(stage);
        run.setLastError(safeError(error));
        run.setCompletedAt(LocalDateTime.now());
        return agentRunRepository.save(run);
    }

    @Transactional
    public AgentStep startStep(
            AgentToolCall call,
            String toolName,
            int attempt,
            int maxAttempts,
            String inputSummary
    ) {
        AgentStep step = new AgentStep();
        step.setAgentRunId(call.agentRunId());
        step.setDailyTaskId(call.dailyTaskId());
        step.setStage(call.stage());
        step.setToolName(toolName);
        step.setStatus(AgentStepStatus.RUNNING);
        step.setAttemptNumber(attempt);
        step.setMaxAttempts(maxAttempts);
        step.setInputSummary(inputSummary);
        step.setStartedAt(LocalDateTime.now());
        return agentStepRepository.save(step);
    }

    @Transactional
    public AgentStep finishStep(Long stepId, String outputSummary) {
        AgentStep step = getStep(stepId);
        step.setStatus(AgentStepStatus.SUCCEEDED);
        step.setOutputSummary(outputSummary);
        completeTiming(step);
        return agentStepRepository.save(step);
    }

    @Transactional
    public AgentStep failStep(
            Long stepId,
            ToolFailureType failureType,
            Throwable error,
            boolean willRetry
    ) {
        AgentStep step = getStep(stepId);
        step.setStatus(willRetry ? AgentStepStatus.RETRYING : AgentStepStatus.FAILED);
        step.setFailureType(failureType);
        step.setErrorMessage(safeError(error));
        completeTiming(step);
        return agentStepRepository.save(step);
    }

    @Transactional(readOnly = true)
    public AgentRunDetails getDetails(Long runId) {
        AgentRun run = getRun(runId);
        return new AgentRunDetails(run, agentStepRepository.findByAgentRunIdOrderByCreatedAtAscIdAsc(runId));
    }

    @Transactional(readOnly = true)
    public AgentRunDetails getLatestDetailsForTask(Long taskId) {
        AgentRun run = agentRunRepository.findTopByDailyTaskIdOrderByCreatedAtDesc(taskId)
                .orElseThrow(() -> new IllegalArgumentException("No agent run exists for taskId=" + taskId));
        return new AgentRunDetails(run, agentStepRepository.findByAgentRunIdOrderByCreatedAtAscIdAsc(run.getId()));
    }

    @Transactional(readOnly = true)
    public List<AgentRun> listRecentRuns() {
        return agentRunRepository.findTop20ByOrderByCreatedAtDesc();
    }

    private AgentRun getRun(Long runId) {
        return agentRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run does not exist, id=" + runId));
    }

    private AgentStep getStep(Long stepId) {
        return agentStepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Agent step does not exist, id=" + stepId));
    }

    private void completeTiming(AgentStep step) {
        LocalDateTime completedAt = LocalDateTime.now();
        step.setCompletedAt(completedAt);
        step.setDurationMs(Duration.between(step.getStartedAt(), completedAt).toMillis());
    }

    private ObjectNode readMemory(AgentRun run) {
        try {
            JsonNode node = objectMapper.readTree(run.getMemorySnapshot());
            return node instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize agent memory", e);
        }
    }

    private String safeError(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        String message = error.getMessage();
        String value = error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
