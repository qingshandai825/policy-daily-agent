package com.itheima.policydailyagent.domain.report;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "monthly_report",
        uniqueConstraints = @UniqueConstraint(name = "uk_monthly_report_period", columnNames = {"report_year", "report_month"}))
public class MonthlyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_year", nullable = false)
    private int reportYear;

    @Column(name = "report_month", nullable = false)
    private int reportMonth;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MonthlyReportStatus status = MonthlyReportStatus.DRAFT;

    @Column(name = "template_version", length = 50)
    private String templateVersion;

    @Column(name = "template_hash", length = 64)
    private String templateHash;

    @Column(name = "content_confirmed_by", length = 100)
    private String contentConfirmedBy;

    @Column(name = "content_confirmed_at")
    private LocalDateTime contentConfirmedAt;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "output_file_name", length = 500)
    private String outputFileName;

    @Column(name = "output_hash", length = 64)
    private String outputHash;

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
