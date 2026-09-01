package com.itheima.policydailyagent.domain.report;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "monthly_report_item_revision",
        uniqueConstraints = @UniqueConstraint(name = "uk_report_item_revision", columnNames = {"report_item_id", "revision_no"}),
        indexes = @Index(name = "idx_report_item_revision_item", columnList = "report_item_id,created_at"))
public class MonthlyReportItemRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_item_id", nullable = false)
    private Long reportItemId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "item_title", length = 500)
    private String itemTitle;

    @Column(name = "section_id")
    private Long sectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", length = 30)
    private ReportItemStatus itemStatus;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "change_type", nullable = false, length = 50)
    private String changeType;

    @Column(name = "editor", nullable = false, length = 100)
    private String editor;

    @Column(name = "change_note", length = 1000)
    private String changeNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
