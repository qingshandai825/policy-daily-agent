package com.itheima.policydailyagent.agent.repository;

import com.itheima.policydailyagent.agent.entity.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentStepRepository extends JpaRepository<AgentStep, Long> {

    List<AgentStep> findByAgentRunIdOrderByCreatedAtAscIdAsc(Long agentRunId);

    void deleteByDailyTaskId(Long dailyTaskId);

    void deleteByAgentRunIdIn(List<Long> agentRunIds);
}
