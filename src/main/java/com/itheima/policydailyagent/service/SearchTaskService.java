package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskStatus;
import com.itheima.policydailyagent.dto.PolicyDiscoverRequest;
import com.itheima.policydailyagent.dto.SearchTaskRunRequest;
import com.itheima.policydailyagent.repository.SearchTaskPolicyRepository;
import com.itheima.policydailyagent.repository.SearchTaskRepository;
import com.itheima.policydailyagent.service.memory.AgentTaskMemoryService;
import com.itheima.policydailyagent.service.search.LeaseLostException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Supplier;

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

    /**
     * 正常完成：以原子条件 UPDATE 写入全量累计计数、终止原因并释放执行权。
     * 仅在当前执行器仍持有执行权时生效；执行权已丢失时抛 {@link LeaseLostException}，
     * 绝不覆盖新执行器的结果。由多轮流程在确定「正常停因」后调用。
     */
    @Transactional
    public SearchTask completeWithReason(
            Long taskId,
            String executorId,
            int foundCount,
            int savedCount,
            int duplicateCount,
            int filteredCount,
            int failedCount,
            String terminationReason
    ) {
        SearchTaskStatus status = failedCount > 0 ? SearchTaskStatus.PARTIAL_FAILED : SearchTaskStatus.COMPLETED;
        int updated = searchTaskRepository.finalizeAsCompleted(
                taskId, executorId, status, foundCount, savedCount, duplicateCount, filteredCount,
                failedCount, terminationReason, LocalDateTime.now());
        if (updated == 0) {
            throw new LeaseLostException(taskId, executorId);
        }
        return searchTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("搜索任务已不存在，id=" + taskId));
    }

    /**
     * 不可恢复失败：以原子条件 UPDATE 保留累计计数与已入库候选（不回滚），标记终止原因并释放执行权。
     * 仅在当前执行器仍持有执行权时生效；执行权已丢失时抛 {@link LeaseLostException}，
     * 绝不覆盖新执行器的结果。
     */
    @Transactional
    public SearchTask failWithCounts(
            Long taskId,
            String executorId,
            int foundCount,
            int savedCount,
            int duplicateCount,
            int filteredCount,
            int failedCount,
            String message,
            String terminationReason
    ) {
        int updated = searchTaskRepository.finalizeAsFailed(
                taskId, executorId, foundCount, savedCount, duplicateCount, filteredCount, failedCount,
                message, terminationReason, LocalDateTime.now());
        if (updated == 0) {
            throw new LeaseLostException(taskId, executorId);
        }
        return searchTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("搜索任务已不存在，id=" + taskId));
    }

    /**
     * 持久化首次运行时的实际生效参数快照（多轮流程在开始执行前调用）。
     */
    @Transactional
    public SearchTask saveRunParams(Long taskId, String runParamsJson) {
        SearchTask task = getTask(taskId);
        task.setRunParamsJson(runParamsJson);
        return searchTaskRepository.save(task);
    }

    /**
     * 首次执行前写入执行器标识与租约（任务刚创建，无并发抢占）。
     */
    @Transactional
    public SearchTask markExecutor(Long taskId, String executorId, LocalDateTime leaseUntil) {
        SearchTask task = getTask(taskId);
        task.setExecutorId(executorId);
        task.setLeaseExpiresAt(leaseUntil);
        return searchTaskRepository.save(task);
    }

    /**
     * 原子抢占执行权（仅用于 resume）。返回 true 表示抢占成功，false 表示已被其他执行器持有或任务已不可恢复。
     */
    @Transactional
    public boolean claimExecutor(Long taskId, String executorId, LocalDateTime leaseUntil, LocalDateTime now) {
        return searchTaskRepository.claimExecutor(taskId, executorId, leaseUntil, now) > 0;
    }

    /**
     * 原子续约（仅当前持有执行权的执行器能续约）。返回 true 表示续约成功，false 表示执行权已丢失。
     * 由多轮流程每轮开始时调用；false 时调用方必须立即终止后续采集与写入。
     */
    @Transactional
    public boolean renewLease(Long taskId, String executorId, LocalDateTime leaseUntil) {
        return searchTaskRepository.renewLease(taskId, executorId, leaseUntil) > 0;
    }

    @Transactional(readOnly = true)
    public SearchTask getTask(Long taskId) {
        return searchTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("搜索任务不存在，id=" + taskId));
    }

    /**
     * 「校验执行权 + 业务写入」原子化原语：在持有任务行悲观锁（SELECT ... FOR UPDATE）的
     * 事务内读取任务、校验 executor_id 与当前执行器一致，然后执行调用方的业务写入。
     *
     * <p>执行权规则（claim / renew / 业务写入 / finalize 统一遵循）：执行权 = executor_id
     * 匹配当前执行器（权威 fencing 令牌）；租约到期只用于「允许抢占」，绝不使仍持有
     * executor_id 的执行器失去执行权。因此旧执行器在续约成功后、写入检查点前被接管，会因
     * executor_id 不再匹配而在此处抛 {@link LeaseLostException}，其检查点写入被原子拒绝，
     * 不会覆盖新执行器的执行权与结果。
     */
    @Transactional
    public <T> T withOwnership(Long taskId, String executorId, Supplier<T> writes) {
        SearchTask task = searchTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new IllegalArgumentException("搜索任务不存在，id=" + taskId));
        if (!executorId.equals(task.getExecutorId())) {
            throw new LeaseLostException(taskId, executorId);
        }
        return writes.get();
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
