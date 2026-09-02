package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.memory.AgentTaskMemory;
import com.itheima.policydailyagent.domain.memory.AgentTaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentTaskMemoryRepository extends JpaRepository<AgentTaskMemory, Long> {

    Optional<AgentTaskMemory> findByTaskTypeAndBusinessTaskId(AgentTaskType taskType, Long businessTaskId);
}
