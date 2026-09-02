package com.itheima.policydailyagent.service.memory;

import com.itheima.policydailyagent.domain.memory.AgentTaskMemory;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.repository.AgentTaskMemoryRepository;
import com.itheima.policydailyagent.repository.SearchTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用真实 JPA 持久化（H2 create-drop + @Version）验证：
 * 1) version_no 由 Hibernate 乐观锁自动维护，不再是应用层手动自增；
 * 2) 用旧版本号写回会被拒绝，避免并发更新被静默覆盖；
 * 3) Memory 缺失时查询路径可按幂等规则补建。
 */
@SpringBootTest
@ActiveProfiles("test")
class AgentTaskMemoryPersistenceTests {

    @Autowired
    private AgentTaskMemoryRepository memoryRepository;

    @Autowired
    private SearchTaskRepository searchTaskRepository;

    @Autowired
    private AgentTaskMemoryService memoryService;

    @Test
    void versionNoIsOptimisticallyManagedAndStaleWriteIsRejected() {
        SearchTask task = searchTaskRepository.saveAndFlush(task("并发测试"));

        AgentTaskMemory memory = memoryService.initialize(task);
        assertThat(memory.isAutoRecovered()).isFalse();
        assertThat(memory.getVersionNo()).isZero();

        Long memoryId = memory.getId();
        // 读取一个“陈旧”副本（version_no = 0），随后业务更新把版本推进到 1
        AgentTaskMemory stale = memoryRepository.findById(memoryId).orElseThrow();

        memoryService.markStarted(task.getId());
        AgentTaskMemory current = memoryRepository.findById(memoryId).orElseThrow();
        assertThat(current.getVersionNo()).isEqualTo(1L);

        // 用旧版本号写回必须被乐观锁拒绝，避免并发更新被静默覆盖
        stale.setSummary("陈旧写回");
        assertThatThrownBy(() -> memoryRepository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void missingMemoryIsRebuiltOnQueryWhenTaskExists() {
        SearchTask task = searchTaskRepository.saveAndFlush(task("补建测试"));

        var view = memoryService.memoryView(task.getId());

        assertThat(view.businessTaskId()).isEqualTo(task.getId());
        assertThat(view.summary()).contains("自动补建");
        assertThat(view.autoRecovered()).isTrue();
    }

    private SearchTask task(String taskName) {
        SearchTask task = new SearchTask();
        task.setTaskName(taskName);
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能");
        task.setSourceIds("gov");
        task.markRunning();
        return task;
    }
}
