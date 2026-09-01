package com.itheima.policydailyagent.domain.report;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "monthly_report_item_source", indexes = {
        @Index(name = "idx_report_item_source_item", columnList = "report_item_id")
})
public class MonthlyReportItemSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_item_id", nullable = false)
    private Long reportItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ReportSourceType sourceType;

    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "source_title", length = 500)
    private String sourceTitle;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "source_content_snapshot", columnDefinition = "TEXT")
    private String sourceContentSnapshot;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
