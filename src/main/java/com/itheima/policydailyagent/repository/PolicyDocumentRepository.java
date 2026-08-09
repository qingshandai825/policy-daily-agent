package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.entity.PolicyDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, Long> {

    boolean existsBySourceUrl(String sourceUrl);

    Optional<PolicyDocument> findBySourceUrl(String sourceUrl);

    boolean existsByContentHash(String contentHash);

    List<PolicyDocument> findTop20ByOrderByCreatedAtDesc();

    List<PolicyDocument> findByDailyTaskIdOrderByPublishDateDescCreatedAtDesc(Long dailyTaskId);

    List<PolicyDocument> findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(Long dailyTaskId, String reviewStatus);

    long countByDailyTaskId(Long dailyTaskId);

    long countByDailyTaskIdAndReviewStatus(Long dailyTaskId, String reviewStatus);

    void deleteByDailyTaskId(Long dailyTaskId);

    List<PolicyDocument> findTop20ByStatusOrderByCreatedAtDesc(String status);

    List<PolicyDocument> findTop20ByStatusOrderByPublishDateDescCreatedAtDesc(String status);

    List<PolicyDocument> findByPublishDateBetweenAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
            LocalDate startDate,
            LocalDate endDate,
            String reviewStatus
    );
}