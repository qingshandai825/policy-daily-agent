package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.search.SearchTaskRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SearchTaskRoundRepository extends JpaRepository<SearchTaskRound, Long> {

    List<SearchTaskRound> findBySearchTaskIdOrderByRoundNoAsc(Long searchTaskId);

    Optional<SearchTaskRound> findBySearchTaskIdAndRoundNo(Long searchTaskId, int roundNo);

    Optional<SearchTaskRound> findFirstBySearchTaskIdOrderByRoundNoDesc(Long searchTaskId);

    boolean existsBySearchTaskIdAndRoundNo(Long searchTaskId, int roundNo);

    void deleteBySearchTaskId(Long searchTaskId);
}
