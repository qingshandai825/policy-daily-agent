package com.itheima.policydailyagent.agent.repository;

import com.itheima.policydailyagent.agent.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {

    Optional<AgentRun> findTopByDailyTaskIdOrderByCreatedAtDesc(Long dailyTaskId);

    List<AgentRun> findTop20ByOrderByCreatedAtDesc();

    List<AgentRun> findAllByDailyTaskId(Long dailyTaskId);

    void deleteByDailyTaskId(Long dailyTaskId);
}
