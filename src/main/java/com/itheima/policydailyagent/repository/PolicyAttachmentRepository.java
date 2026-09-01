package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.entity.PolicyAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyAttachmentRepository extends JpaRepository<PolicyAttachment, Long> {

    List<PolicyAttachment> findByPolicyIdOrderByIdAsc(Long policyId);

    Optional<PolicyAttachment> findByPolicyIdAndSourceUrl(Long policyId, String sourceUrl);
}
