package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.entity.PolicyDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, Long> {

    boolean existsBySourceUrl(String sourceUrl);

    Optional<PolicyDocument> findBySourceUrl(String sourceUrl);

    boolean existsByContentHash(String contentHash);

    Optional<PolicyDocument> findFirstByContentHash(String contentHash);

    List<PolicyDocument> findTop20ByOrderByCreatedAtDesc();

    List<PolicyDocument> findByReviewStatusOrderByPublishDateDescCreatedAtDesc(
            PolicyReviewStatus reviewStatus);

    List<PolicyDocument> findBySearchTaskIdOrderByPublishDateDescCreatedAtDesc(Long searchTaskId);

    List<PolicyDocument> findBySearchTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
            Long searchTaskId,
            PolicyReviewStatus reviewStatus
    );

    long countBySearchTaskId(Long searchTaskId);

    long countBySearchTaskIdAndReviewStatus(Long searchTaskId, PolicyReviewStatus reviewStatus);
}
