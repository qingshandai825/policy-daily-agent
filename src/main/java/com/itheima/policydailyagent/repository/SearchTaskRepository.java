package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.search.SearchTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchTaskRepository extends JpaRepository<SearchTask, Long> {

    List<SearchTask> findTop20ByOrderByCreatedAtDesc();
}
