package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.agent.entity.AgentRun;
import com.itheima.policydailyagent.agent.repository.AgentRunRepository;
import com.itheima.policydailyagent.agent.repository.AgentStepRepository;
import com.itheima.policydailyagent.dto.PolicyDiscoverRequest;
import com.itheima.policydailyagent.entity.DailyTask;
import com.itheima.policydailyagent.repository.DailyTaskRepository;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class DailyTaskService {

    private final DailyTaskRepository dailyTaskRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;

    public DailyTaskService(
            DailyTaskRepository dailyTaskRepository,
            PolicyDocumentRepository policyDocumentRepository,
            AgentRunRepository agentRunRepository,
            AgentStepRepository agentStepRepository
    ) {
        this.dailyTaskRepository = dailyTaskRepository;
        this.policyDocumentRepository = policyDocumentRepository;
        this.agentRunRepository = agentRunRepository;
        this.agentStepRepository = agentStepRepository;
    }

    @Transactional
    public DailyTask startOrReuse(PolicyDiscoverRequest request, List<String> normalizedKeywords) {
        DailyTask task = request.taskId() == null
                ? new DailyTask()
                : dailyTaskRepository.findById(request.taskId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "政策采集任务不存在，id=" + request.taskId()
                ));

        if (request.taskId() == null || hasText(request.taskName())) {
            task.setTaskName(resolveTaskName(request));
        }
        task.setTargetStartDate(request.resolvedTargetStartDate());
        task.setTargetEndDate(request.resolvedTargetEndDate());
        task.setTopics(String.join(",", normalizedKeywords));
        task.setSourceUrl(request.listPageUrl());
        task.markRunning();
        return dailyTaskRepository.save(task);
    }

    @Transactional
    public DailyTask complete(
            Long taskId,
            int foundCount,
            int savedCount,
            int duplicateCount,
            int filteredCount,
            int failedCount,
            int summarizedCount
    ) {
        DailyTask task = dailyTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Daily task does not exist, id=" + taskId));

        task.setFoundCount(task.getFoundCount() + foundCount);
        task.setSavedCount(task.getSavedCount() + savedCount);
        task.setDuplicateCount(task.getDuplicateCount() + duplicateCount);
        task.setFilteredCount(task.getFilteredCount() + filteredCount);
        task.setFailedCount(task.getFailedCount() + failedCount);
        task.setSummarizedCount(task.getSummarizedCount() + summarizedCount);
        task.markCompleted();

        return dailyTaskRepository.save(task);
    }

    @Transactional
    public void deleteTask(Long taskId) {
        DailyTask task = dailyTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("政策采集任务不存在，id=" + taskId));
        List<Long> runIds = agentRunRepository.findAllByDailyTaskId(taskId).stream()
                .map(AgentRun::getId)
                .toList();

        agentStepRepository.deleteByDailyTaskId(taskId);
        if (!runIds.isEmpty()) {
            agentStepRepository.deleteByAgentRunIdIn(runIds);
        }
        agentRunRepository.deleteByDailyTaskId(taskId);
        policyDocumentRepository.deleteByDailyTaskId(taskId);
        dailyTaskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public List<DailyTask> listRecentTasks() {
        return dailyTaskRepository.findTop20ByOrderByCreatedAtDesc();
    }

    private String resolveTaskName(PolicyDiscoverRequest request) {
        if (hasText(request.taskName())) {
            return request.taskName().trim();
        }
        LocalDate startDate = request.resolvedTargetStartDate();
        LocalDate endDate = request.resolvedTargetEndDate();
        if (startDate == null && endDate == null) {
            return "政策素材工作区 " + LocalDate.now();
        }
        if (startDate != null && startDate.equals(endDate)) {
            return "政策日报素材 " + startDate;
        }
        return "政策日报素材 " + safeDate(startDate) + " 至 " + safeDate(endDate);
    }

    private String safeDate(LocalDate date) {
        return date == null ? "不限" : date.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}