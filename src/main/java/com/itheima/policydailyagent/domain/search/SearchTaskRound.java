package com.itheima.policydailyagent.domain.search;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 一次搜索任务中的单个执行轮次。只保存计划、关键词/信源集合、
 * 前后主题覆盖度快照与计数，绝不写政策正文或附件。
 */
@Getter
@Setter
@Entity
@Table(
        name = "search_task_round",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_search_task_round_retry",
                columnNames = {"search_task_id", "round_no", "retry_no"}
        ),
        indexes = @Index(name = "idx_search_task_round_task", columnList = "search_task_id, round_no")
)
public class SearchTaskRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "search_task_id", nullable = false)
    private Long searchTaskId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    /**
     * 同一逻辑轮次内的第 N 次尝试（0 = 首次）。中断（PLANNED/RUNNING/INTERRUPTED）后
     * 恢复时以 retry_no+1 原地重试同一 round_no，而非跳到 round_no+1 丢弃原始计划。
     */
    @Column(name = "retry_no", nullable = false)
    private int retryNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SearchTaskRoundStatus status = SearchTaskRoundStatus.PLANNED;

    @Column(name = "plan_json", columnDefinition = "TEXT")
    private String planJson;

    @Column(name = "keywords_json", columnDefinition = "TEXT")
    private String keywordsJson;

    @Column(name = "target_sources_json", columnDefinition = "TEXT")
    private String targetSourcesJson;

    @Column(name = "coverage_before_json", columnDefinition = "TEXT")
    private String coverageBeforeJson;

    @Column(name = "coverage_after_json", columnDefinition = "TEXT")
    private String coverageAfterJson;

    @Column(name = "search_feedback_json", columnDefinition = "TEXT")
    private String searchFeedbackJson;

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

    @Column(name = "new_association_count", nullable = false)
    private int newAssociationCount;

    @Column(name = "stop_reason", length = 200)
    private String stopReason;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
