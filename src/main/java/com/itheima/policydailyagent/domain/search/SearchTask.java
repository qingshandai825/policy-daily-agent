package com.itheima.policydailyagent.domain.search;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "search_task", indexes = {
        @Index(name = "idx_search_task_status", columnList = "status"),
        @Index(name = "idx_search_task_report_month", columnList = "report_month"),
        @Index(name = "idx_search_task_date_range", columnList = "target_start_date,target_end_date")
})
public class SearchTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Column(name = "report_month", length = 7)
    private String reportMonth;

    @Column(name = "target_start_date", nullable = false)
    private LocalDate targetStartDate;

    @Column(name = "target_end_date", nullable = false)
    private LocalDate targetEndDate;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "source_ids", columnDefinition = "TEXT")
    private String sourceIds;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SearchTaskStatus status = SearchTaskStatus.CREATED;

    @Column(name = "found_count", nullable = false)
    private int foundCount;

    @Column(name = "saved_count", nullable = false)
    private int savedCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "filtered_count", nullable = false)
    private int filteredCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    /**
     * 持有执行权的执行器标识（单机 DB 租约）。执行中写入，正常结束/失败时清空。
     */
    @Column(name = "executor_id", length = 100)
    private String executorId;

    /**
     * 执行租约过期时间。崩溃后到期即可被下一个 resume 接管；正常结束/失败时清空。
     */
    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    /**
     * 终止原因：正常停因（MAX_ROUNDS_REACHED/ALL_TOPICS_COVERED/NO_GROWTH/NO_NEW_PLAN）
     * 或失败停因（TASK_ERROR/NO_AVAILABLE_SOURCE）。用于拒绝「已正常完成」任务的恢复。
     */
    @Column(name = "termination_reason", length = 50)
    private String terminationReason;

    /**
     * 首次运行时的实际生效参数快照（JSON），resume 据此恢复原始参数而非当前默认配置。
     */
    @Column(name = "run_params_json", columnDefinition = "TEXT")
    private String runParamsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void markRunning() {
        status = SearchTaskStatus.RUNNING;
        startedAt = LocalDateTime.now();
    }

    public void markCompleted() {
        status = failedCount > 0 ? SearchTaskStatus.PARTIAL_FAILED : SearchTaskStatus.COMPLETED;
        completedAt = LocalDateTime.now();
    }

    public void markFailed(String message) {
        status = SearchTaskStatus.FAILED;
        failureMessage = message;
        completedAt = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
