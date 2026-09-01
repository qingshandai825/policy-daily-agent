package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.policy.PolicyReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyReviewRepository extends JpaRepository<PolicyReview, Long> {

    List<PolicyReview> findByPolicyIdOrderByReviewedAtDesc(Long policyId);
}
