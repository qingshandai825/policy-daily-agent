package com.itheima.policydailyagent.domain.report;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "monthly_report_item", indexes = {
        @Index(name = "idx_report_item_report_section", columnList = "report_id,section_id,sort_order")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_item_report_policy", columnNames = {"report_id", "policy_id"})
})
public class MonthlyReportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "analysis_id")
    private Long analysisId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ReportSourceType sourceType;

    @Column(name = "item_title", length = 500)
    private String itemTitle;

    @Column(name = "agent_draft", columnDefinition = "TEXT")
    private String agentDraft;

    @Column(name = "final_content", columnDefinition = "TEXT")
    private String finalContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReportItemStatus status = ReportItemStatus.DRAFT;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

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
