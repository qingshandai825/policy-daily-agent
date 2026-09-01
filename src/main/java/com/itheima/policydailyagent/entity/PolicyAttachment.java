package com.itheima.policydailyagent.entity;

import com.itheima.policydailyagent.domain.policy.AttachmentExtractionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "policy_attachment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_policy_attachment_policy_url",
                columnNames = {"policy_id", "source_url"}
        ),
        indexes = @Index(name = "idx_policy_attachment_policy", columnList = "policy_id")
)
public class PolicyAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "source_url", nullable = false, length = 1500)
    private String sourceUrl;

    @Column(name = "file_type", length = 30)
    private String fileType;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 30)
    private AttachmentExtractionStatus extractionStatus = AttachmentExtractionStatus.DETECTED;

    @Column(name = "extracted_content", columnDefinition = "TEXT")
    private String extractedContent;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "content_length")
    private Integer contentLength;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (detectedAt == null) {
            detectedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
