package com.itheima.policydailyagent.domain.search;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 任务内信源级失败记录：TLS/证书/连接等导致某个固定信源整轮采集失败。
 * 与「单条政策处理失败」不同——后者不代表整个信源不可用；本表只记录前者，
 * 用于后续轮次规划与执行排除该信源，并在恢复后依然有效。
 */
@Getter
@Setter
@Entity
@Table(
        name = "search_task_source_failure",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_search_task_source_failure",
                columnNames = {"search_task_id", "source_id"}
        ),
        indexes = @Index(name = "idx_search_task_source_failure_task", columnList = "search_task_id")
)
public class SearchTaskSourceFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "search_task_id", nullable = false)
    private Long searchTaskId;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(name = "source_name", length = 200)
    private String sourceName;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "first_round_no", nullable = false)
    private int firstRoundNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
