package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.search.SearchTaskPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SearchTaskPolicyRepository extends JpaRepository<SearchTaskPolicy, Long> {

    boolean existsBySearchTaskIdAndPolicyId(Long searchTaskId, Long policyId);

    Optional<SearchTaskPolicy> findBySearchTaskIdAndPolicyId(Long searchTaskId, Long policyId);

    List<SearchTaskPolicy> findBySearchTaskIdOrderByDiscoveryOrderAscIdAsc(Long searchTaskId);
}
