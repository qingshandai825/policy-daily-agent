package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.config.PolicySourceProperties;
import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskSourceFailure;
import com.itheima.policydailyagent.repository.SearchTaskSourceFailureRepository;
import com.itheima.policydailyagent.service.SearchTaskService;
import com.itheima.policydailyagent.service.memory.AgentTaskMemoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 缺陷3（轮次中途持久化检查点）单元测试：验证信源级失败在信源边界即被持久化到
 * search_task_source_failure，而非等整轮 execute 返回后才由多轮服务兜底保存。
 */
class SearchRoundExecutorTests {

    @Test
    void executorPersistsSourceFailureAtBoundary() {
        PolicySourceProperties sourceProperties = mock(PolicySourceProperties.class);
        PolicySiteAdapter adapter = mock(PolicySiteAdapter.class);
        PolicyCandidateIngestService ingest = mock(PolicyCandidateIngestService.class);
        AgentTaskMemoryService memory = mock(AgentTaskMemoryService.class);
        SearchTaskSourceFailureRepository sourceFailureRepository =
                mock(SearchTaskSourceFailureRepository.class);
        when(sourceFailureRepository.existsBySearchTaskIdAndSourceId(anyLong(), anyString()))
                .thenReturn(false);

        PolicySourceProperties.Item miit = new PolicySourceProperties.Item();
        miit.setId("miit-policy");
        miit.setName("工业和信息化部");
        miit.setUrl("https://www.miit.gov.cn/policy/");

        when(adapter.supports(anyString())).thenReturn(true);
        when(adapter.discover(eq(miit.getUrl()), anyList(), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("TLS handshake failed"));

        SearchRoundExecutor executor = new SearchRoundExecutor(
                sourceProperties, List.of(adapter), ingest, memory, sourceFailureRepository,
                mock(SearchTaskService.class));

        SearchTask task = new SearchTask();
        task.setId(1L);
        task.setKeywords("人工智能");

        RoundExecution execution = executor.execute(task, List.of("人工智能"), List.of(miit), 5, true, 1, null);

        assertThat(execution.failed()).isEqualTo(1);
        assertThat(execution.sourceFailures()).hasSize(1);

        verify(sourceFailureRepository).save(argThat((SearchTaskSourceFailure f) ->
                f.getSourceId().equals("miit-policy")
                        && "TLS handshake failed".equals(f.getMessage())
                        && f.getFirstRoundNo() == 1));
    }

    /**
     * 缺陷2 场景 C：检查点已提交但调用方被打断后，重放同一信源失败工作项 —— 幂等，不重复写检查点。
     */
    @Test
    void executorSkipsDuplicateSourceFailureCheckpoint() {
        PolicySourceProperties sourceProperties = mock(PolicySourceProperties.class);
        PolicySiteAdapter adapter = mock(PolicySiteAdapter.class);
        PolicyCandidateIngestService ingest = mock(PolicyCandidateIngestService.class);
        AgentTaskMemoryService memory = mock(AgentTaskMemoryService.class);
        SearchTaskSourceFailureRepository sourceFailureRepository =
                mock(SearchTaskSourceFailureRepository.class);
        when(sourceFailureRepository.existsBySearchTaskIdAndSourceId(anyLong(), anyString()))
                .thenReturn(true); // 检查点已提交

        PolicySourceProperties.Item miit = new PolicySourceProperties.Item();
        miit.setId("miit-policy");
        miit.setName("工业和信息化部");
        miit.setUrl("https://www.miit.gov.cn/policy/");

        when(adapter.supports(anyString())).thenReturn(true);
        when(adapter.discover(eq(miit.getUrl()), anyList(), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("TLS handshake failed"));

        SearchRoundExecutor executor = new SearchRoundExecutor(
                sourceProperties, List.of(adapter), ingest, memory, sourceFailureRepository,
                mock(SearchTaskService.class));

        SearchTask task = new SearchTask();
        task.setId(1L);
        task.setKeywords("人工智能");

        RoundExecution execution = executor.execute(task, List.of("人工智能"), List.of(miit), 5, true, 1, null);

        // 失败仍计入本轮统计，但检查点不重复写入（同一任务同一信源只落一次）。
        assertThat(execution.failed()).isEqualTo(1);
        verify(sourceFailureRepository, never()).save(any(SearchTaskSourceFailure.class));
    }
}
