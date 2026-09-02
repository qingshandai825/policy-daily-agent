package com.itheima.policydailyagent.domain.memory;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 任务级 Agent Memory：为一次业务任务（如一次搜索、一次分析、一次月报生成）
 * 保存目标、当前阶段、摘要、结构化上下文快照与下一步动作，用于持久化、
 * 追溯与失败恢复。业务任务通过 (task_type, business_task_id) 唯一定位，
 * 不强制外键到具体业务表，以支持多种任务类型的扩展。
 */
@Getter
@Setter
@Entity
@Table(
        name = "agent_task_memory",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_agent_task_memory_task",
                columnNames = {"task_type", "business_task_id"}
        ),
        indexes = @Index(name = "idx_agent_task_memory_type_status", columnList = "task_type,memory_status")
)
public class AgentTaskMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 30)
    private AgentTaskType taskType;

    @Column(name = "business_task_id", nullable = false)
    private Long businessTaskId;

    @Column(name = "goal", columnDefinition = "TEXT")
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", nullable = false, length = 30)
    private AgentTaskPhase currentPhase;

    @Enumerated(EnumType.STRING)
    @Column(name = "memory_status", nullable = false, length = 30)
    private AgentTaskMemoryStatus memoryStatus;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "context_json", columnDefinition = "TEXT")
    private String contextJson;

    @Column(name = "next_action", columnDefinition = "TEXT")
    private String nextAction;

    @Column(name = "auto_recovered", nullable = false)
    private boolean autoRecovered;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
