package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.search.SearchTaskSourceFailure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchTaskSourceFailureRepository extends JpaRepository<SearchTaskSourceFailure, Long> {

    List<SearchTaskSourceFailure> findBySearchTaskIdOrderByFirstRoundNoAsc(Long searchTaskId);

    boolean existsBySearchTaskIdAndSourceId(Long searchTaskId, String sourceId);

    void deleteBySearchTaskId(Long searchTaskId);
}
