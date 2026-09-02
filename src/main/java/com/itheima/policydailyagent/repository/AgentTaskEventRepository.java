package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.memory.AgentTaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentTaskEventRepository extends JpaRepository<AgentTaskEvent, Long> {

    List<AgentTaskEvent> findByMemoryIdOrderByIdAsc(Long memoryId);

    void deleteByMemoryId(Long memoryId);
}
