package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.PolicyDiscoverRequest;
import com.itheima.policydailyagent.entity.DailyTask;
import com.itheima.policydailyagent.repository.DailyTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class DailyTaskService {

    private final DailyTaskRepository dailyTaskRepository;

    public DailyTaskService(DailyTaskRepository dailyTaskRepository) {
        this.dailyTaskRepository = dailyTaskRepository;
    }

    @Transactional
    public DailyTask createAndStart(PolicyDiscoverRequest request, List<String> normalizedKeywords) {
        DailyTask task = new DailyTask();
        task.setTaskName(resolveTaskName(request));
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

        task.setFoundCount(foundCount);
        task.setSavedCount(savedCount);
        task.setDuplicateCount(duplicateCount);
        task.setFilteredCount(filteredCount);
        task.setFailedCount(failedCount);
        task.setSummarizedCount(summarizedCount);
        task.markCompleted();

        return dailyTaskRepository.save(task);
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

        if (startDate != null && endDate != null && startDate.equals(endDate)) {
            return "Policy daily task " + startDate;
        }

        if (startDate != null || endDate != null) {
            return "Policy daily task " + safeDate(startDate) + " to " + safeDate(endDate);
        }

        return "Policy daily task";
    }

    private String safeDate(LocalDate date) {
        return date == null ? "open" : date.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
