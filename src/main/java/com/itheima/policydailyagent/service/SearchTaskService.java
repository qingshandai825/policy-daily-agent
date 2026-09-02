package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskStatus;
import com.itheima.policydailyagent.dto.PolicyDiscoverRequest;
import com.itheima.policydailyagent.dto.SearchTaskRunRequest;
import com.itheima.policydailyagent.repository.SearchTaskPolicyRepository;
import com.itheima.policydailyagent.repository.SearchTaskRepository;
import com.itheima.policydailyagent.service.memory.AgentTaskMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class SearchTaskService {

    private final SearchTaskRepository searchTaskRepository;
    private final SearchTaskPolicyRepository searchTaskPolicyRepository;
    private final AgentTaskMemoryService memoryService;

    public SearchTaskService(
            SearchTaskRepository searchTaskRepository,
            SearchTaskPolicyRepository searchTaskPolicyRepository,
            AgentTaskMemoryService memoryService
    ) {
        this.searchTaskRepository = searchTaskRepository;
        this.searchTaskPolicyRepository = searchTaskPolicyRepository;
        this.memoryService = memoryService;
    }

    @Transactional
    public SearchTask createAndStart(PolicyDiscoverRequest request, List<String> normalizedKeywords) {
        SearchTask task = new SearchTask();
        task.setTaskName(resolveTaskName(request));
        task.setReportMonth(resolveReportMonth(request));
        task.setTargetStartDate(request.resolvedTargetStartDate());
        task.setTargetEndDate(request.resolvedTargetEndDate());
        task.setKeywords(String.join(",", normalizedKeywords));
        task.setSourceUrl(request.listPageUrl());
        task.markRunning();
        return searchTaskRepository.save(task);
    }

    @Transactional
    public SearchTask createAndStart(
            SearchTaskRunRequest request,
            List<String> normalizedKeywords,
            List<String> sourceIds
    ) {
        SearchTask task = new SearchTask();
        task.setTaskName(resolveTaskName(request));
        task.setReportMonth(resolveReportMonth(request));
        task.setTargetStartDate(request.targetStartDate());
        task.setTargetEndDate(request.targetEndDate());
        task.setKeywords(String.join(",", normalizedKeywords));
        task.setSourceIds(String.join(",", sourceIds));
        task.markRunning();
        return searchTaskRepository.save(task);
    }

    @Transactional
    public SearchTask complete(
            Long taskId,
            int foundCount,
            int savedCount,
            int duplicateCount,
            int filteredCount,
            int failedCount
    ) {
        SearchTask task = getTask(taskId);
        task.setFoundCount(foundCount);
        task.setSavedCount(savedCount);
        task.setDuplicateCount(duplicateCount);
        task.setFilteredCount(filteredCount);
        task.setFailedCount(failedCount);
        task.markCompleted();
        return searchTaskRepository.save(task);
    }

    @Transactional
    public SearchTask fail(Long taskId, int foundCount, int failedCount, String message) {
        SearchTask task = getTask(taskId);
        task.setFoundCount(foundCount);
        task.setFailedCount(failedCount);
        task.markFailed(message);
        return searchTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public SearchTask getTask(Long taskId) {
        return searchTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("搜索任务不存在，id=" + taskId));
    }

    @Transactional(readOnly = true)
    public List<SearchTask> listRecentTasks() {
        return searchTaskRepository.findTop20ByOrderByCreatedAtDesc();
    }

    @Transactional
    public void deleteTask(Long taskId) {
        SearchTask task = searchTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("搜索任务不存在，id=" + taskId));
        if (task.getStatus() == SearchTaskStatus.RUNNING) {
            throw new IllegalStateException("任务正在执行中，暂不能删除，请等待完成后重试。");
        }
        searchTaskPolicyRepository.deleteBySearchTaskId(taskId);
        memoryService.deleteForSearchTask(taskId);
        searchTaskRepository.deleteById(taskId);
    }

    private String resolveTaskName(PolicyDiscoverRequest request) {
        if (hasText(request.taskName())) {
            return request.taskName().trim();
        }
        String month = resolveReportMonth(request);
        return month == null ? "政策月报候选检索" : month + "政策月报候选检索";
    }

    private String resolveTaskName(SearchTaskRunRequest request) {
        if (hasText(request.taskName())) {
            return request.taskName().trim();
        }
        String month = resolveReportMonth(request);
        return month == null ? "政策月报候选检索" : month + "政策月报候选检索";
    }

    private String resolveReportMonth(PolicyDiscoverRequest request) {
        if (hasText(request.reportMonth())) {
            return request.reportMonth().trim();
        }
        LocalDate start = request.resolvedTargetStartDate();
        LocalDate end = request.resolvedTargetEndDate();
        YearMonth startMonth = YearMonth.from(start);
        return startMonth.equals(YearMonth.from(end)) ? startMonth.toString() : null;
    }

    private String resolveReportMonth(SearchTaskRunRequest request) {
        if (hasText(request.reportMonth())) {
            return request.reportMonth().trim();
        }
        if (request.targetStartDate() == null || request.targetEndDate() == null) {
            return null;
        }
        YearMonth startMonth = YearMonth.from(request.targetStartDate());
        return startMonth.equals(YearMonth.from(request.targetEndDate())) ? startMonth.toString() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
