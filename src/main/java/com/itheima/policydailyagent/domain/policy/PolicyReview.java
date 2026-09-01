package com.itheima.policydailyagent.domain.policy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "policy_review", indexes = {
        @Index(name = "idx_policy_review_policy", columnList = "policy_id,reviewed_at")
})
public class PolicyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private PolicyReviewStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private PolicyReviewStatus reviewStatus;

    @Column(name = "reviewed_by", nullable = false, length = 100)
    private String reviewedBy;

    @Column(name = "review_comment", length = 1000)
    private String reviewComment;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @PrePersist
    void prePersist() {
        if (reviewedAt == null) {
            reviewedAt = LocalDateTime.now();
        }
    }
}
