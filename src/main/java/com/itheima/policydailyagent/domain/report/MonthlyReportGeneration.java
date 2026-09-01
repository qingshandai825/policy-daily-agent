package com.itheima.policydailyagent.domain.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "monthly_report_generation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_report_generation_no",
                columnNames = {"report_id", "generation_no"}
        ),
        indexes = @Index(
                name = "idx_report_generation_report",
                columnList = "report_id,generation_no"
        ))
public class MonthlyReportGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "generation_no", nullable = false)
    private int generationNo;

    @Column(name = "issue_no", nullable = false)
    private int issueNo;

    @Column(name = "total_issue_no", nullable = false)
    private int totalIssueNo;

    @Column(name = "report_month", nullable = false, length = 50)
    private String reportMonth;

    @Column(name = "report_to", nullable = false, length = 1000)
    private String reportTo;

    @Column(name = "send_to", nullable = false, length = 1000)
    private String sendTo;

    @Column(name = "contact_info", nullable = false, length = 1000)
    private String contactInfo;

    @Column(name = "generated_by", nullable = false, length = 100)
    private String generatedBy;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "file_content", nullable = false, columnDefinition = "bytea")
    private byte[] fileContent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
