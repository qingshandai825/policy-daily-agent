package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.analysis.PolicyAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyAnalysisRepository extends JpaRepository<PolicyAnalysis, Long> {

    List<PolicyAnalysis> findByPolicyIdOrderByVersionNoDesc(Long policyId);

    Optional<PolicyAnalysis> findTopByPolicyIdOrderByVersionNoDesc(Long policyId);
}
