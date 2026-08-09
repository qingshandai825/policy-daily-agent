package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.entity.DailyTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyTaskRepository extends JpaRepository<DailyTask, Long> {

    List<DailyTask> findTop20ByOrderByCreatedAtDesc();
}
