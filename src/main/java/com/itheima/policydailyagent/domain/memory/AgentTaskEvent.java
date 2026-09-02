package com.itheima.policydailyagent.domain.memory;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Agent 任务事件记录。原则上不可变（append-only）：只新增、不修改、不删除，
 * 通过 ID 递增保证稳定的时间顺序，用于追溯任务的每一步动作与决策。
 */
@Getter
@Setter
@Entity
@Table(
        name = "agent_task_event",
        indexes = @Index(name = "idx_agent_task_event_memory", columnList = "memory_id,id")
)
public class AgentTaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "memory_id", nullable = false)
    private Long memoryId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AgentTaskEventType eventType;

    @Column(name = "input_json", columnDefinition = "TEXT")
    private String inputJson;

    @Column(name = "output_json", columnDefinition = "TEXT")
    private String outputJson;

    @Column(name = "decision", columnDefinition = "TEXT")
    private String decision;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
