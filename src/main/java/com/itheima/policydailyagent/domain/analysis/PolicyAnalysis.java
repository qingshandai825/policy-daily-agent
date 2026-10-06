package com.itheima.policydailyagent.domain.analysis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "policy_analysis",
        uniqueConstraints = @UniqueConstraint(name = "uk_policy_analysis_version", columnNames = {"policy_id", "version_no"}),
        indexes = @Index(name = "idx_policy_analysis_policy", columnList = "policy_id,created_at"))
public class PolicyAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_status", nullable = false, length = 30)
    private AnalysisRunStatus runStatus = AnalysisRunStatus.RUNNING;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Column(name = "basic_info_json", columnDefinition = "TEXT")
    private String basicInfoJson;

    @Column(name = "core_content", columnDefinition = "TEXT")
    private String coreContent;

    @Column(name = "relevant_content", columnDefinition = "TEXT")
    private String relevantContent;

    @Column(name = "recommended_section_code", length = 100)
    private String recommendedSectionCode;

    @Column(name = "recommendation_reason", columnDefinition = "TEXT")
    private String recommendationReason;

    @Column(name = "generated_title", length = 500)
    private String generatedTitle;

    @Column(name = "generated_content", columnDefinition = "TEXT")
    private String generatedContent;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "quality_report_json", columnDefinition = "TEXT")
    private String qualityReportJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
