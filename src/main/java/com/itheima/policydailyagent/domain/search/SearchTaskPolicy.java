package com.itheima.policydailyagent.domain.search;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "search_task_policy",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_search_task_policy",
                columnNames = {"search_task_id", "policy_id"}
        ),
        indexes = {
                @Index(name = "idx_search_task_policy_task", columnList = "search_task_id,discovery_order"),
                @Index(name = "idx_search_task_policy_policy", columnList = "policy_id")
        }
)
public class SearchTaskPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "search_task_id", nullable = false)
    private Long searchTaskId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "source_id", length = 100)
    private String sourceId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "discovered_url", nullable = false, length = 1000)
    private String discoveredUrl;

    @Column(name = "discovered_title", length = 500)
    private String discoveredTitle;

    @Column(name = "search_snippet", columnDefinition = "TEXT")
    private String searchSnippet;

    @Column(name = "discovery_order", nullable = false)
    private int discoveryOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (provider == null || provider.isBlank()) {
            provider = "FIXED_SOURCE";
        }
        createdAt = LocalDateTime.now();
    }
}
