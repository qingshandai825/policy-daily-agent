package com.itheima.policydailyagent.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "daily_task",
        indexes = {
                @Index(name = "idx_daily_task_status", columnList = "status"),
                @Index(name = "idx_daily_task_target_date", columnList = "target_start_date,target_end_date")
        }
)
public class DailyTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Column(name = "target_start_date")
    private LocalDate targetStartDate;

    @Column(name = "target_end_date")
    private LocalDate targetEndDate;

    @Column(name = "topics", columnDefinition = "TEXT")
    private String topics;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "CREATED";

    @Column(name = "found_count")
    private int foundCount;

    @Column(name = "saved_count")
    private int savedCount;

    @Column(name = "duplicate_count")
    private int duplicateCount;

    @Column(name = "filtered_count")
    private int filteredCount;

    @Column(name = "failed_count")
    private int failedCount;

    @Column(name = "summarized_count")
    private int summarizedCount;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void markRunning() {
        this.status = "RUNNING";
        this.startedAt = LocalDateTime.now();
    }

    public void markCompleted() {
        this.status = failedCount > 0 ? "PARTIAL_FAILED" : "COMPLETED";
        this.completedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public LocalDate getTargetStartDate() {
        return targetStartDate;
    }

    public void setTargetStartDate(LocalDate targetStartDate) {
        this.targetStartDate = targetStartDate;
    }

    public LocalDate getTargetEndDate() {
        return targetEndDate;
    }

    public void setTargetEndDate(LocalDate targetEndDate) {
        this.targetEndDate = targetEndDate;
    }

    public String getTopics() {
        return topics;
    }

    public void setTopics(String topics) {
        this.topics = topics;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getStatus() {
        return status;
    }

    public int getFoundCount() {
        return foundCount;
    }

    public void setFoundCount(int foundCount) {
        this.foundCount = foundCount;
    }

    public int getSavedCount() {
        return savedCount;
    }

    public void setSavedCount(int savedCount) {
        this.savedCount = savedCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public int getFilteredCount() {
        return filteredCount;
    }

    public void setFilteredCount(int filteredCount) {
        this.filteredCount = filteredCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public int getSummarizedCount() {
        return summarizedCount;
    }

    public void setSummarizedCount(int summarizedCount) {
        this.summarizedCount = summarizedCount;
    }
}
