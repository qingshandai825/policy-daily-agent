package com.itheima.policydailyagent.agent.entity;

import com.itheima.policydailyagent.agent.model.AgentRunStatus;
import com.itheima.policydailyagent.agent.model.AgentStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "agent_run",
        indexes = {
                @Index(name = "idx_agent_run_task", columnList = "daily_task_id"),
                @Index(name = "idx_agent_run_status", columnList = "status"),
                @Index(name = "idx_agent_run_created", columnList = "created_at")
        }
)
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "daily_task_id")
    private Long dailyTaskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AgentRunStatus status = AgentRunStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", length = 50)
    private AgentStage currentStage;

    @Column(name = "input_snapshot", columnDefinition = "TEXT")
    private String inputSnapshot;

    @Column(name = "memory_snapshot", columnDefinition = "TEXT")
    private String memorySnapshot;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "waiting_review_at")
    private LocalDateTime waitingReviewAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

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

    public Long getId() {
        return id;
    }

    public Long getDailyTaskId() {
        return dailyTaskId;
    }

    public void setDailyTaskId(Long dailyTaskId) {
        this.dailyTaskId = dailyTaskId;
    }

    public AgentRunStatus getStatus() {
        return status;
    }

    public void setStatus(AgentRunStatus status) {
        this.status = status;
    }

    public AgentStage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(AgentStage currentStage) {
        this.currentStage = currentStage;
    }

    public String getInputSnapshot() {
        return inputSnapshot;
    }

    public void setInputSnapshot(String inputSnapshot) {
        this.inputSnapshot = inputSnapshot;
    }

    public String getMemorySnapshot() {
        return memorySnapshot;
    }

    public void setMemorySnapshot(String memorySnapshot) {
        this.memorySnapshot = memorySnapshot;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getWaitingReviewAt() {
        return waitingReviewAt;
    }

    public void setWaitingReviewAt(LocalDateTime waitingReviewAt) {
        this.waitingReviewAt = waitingReviewAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
