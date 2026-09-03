package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.domain.search.SearchTaskStatus;
import com.itheima.policydailyagent.dto.SearchTaskRunResult;
import com.itheima.policydailyagent.service.SearchTaskService;
import com.itheima.policydailyagent.service.memory.AgentTaskMemoryService;
import com.itheima.policydailyagent.service.search.PolicySearchOrchestrator;
import com.itheima.policydailyagent.service.search.SearchRoundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 恢复入口的 HTTP 冒烟测试：验证 POST /api/search-tasks/{id}/resume 的
 * 状态码映射（成功 200、不可恢复/并发冲突 400），不依赖真实数据库或外网。
 */
@WebMvcTest(SearchTaskController.class)
class SearchTaskControllerResumeSmokeTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchTaskService searchTaskService;

    @MockBean
    private PolicySearchOrchestrator searchOrchestrator;

    @MockBean
    private AgentTaskMemoryService memoryService;

    @MockBean
    private SearchRoundService roundService;

    @Test
    void resumeReturnsOkWhenServiceSucceeds() throws Exception {
        when(roundService.resume(1L)).thenReturn(new SearchTaskRunResult(
                1L, SearchTaskStatus.COMPLETED, 1, 1, 0, 0, 0, 1, 2, List.of(), false));

        mockMvc.perform(post("/api/search-tasks/1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.roundCount").value(2));
    }

    @Test
    void resumeReturnsBadRequestWhenAlreadyCompleted() throws Exception {
        when(roundService.resume(1L)).thenThrow(new IllegalStateException("任务已正常完成，无法恢复"));

        mockMvc.perform(post("/api/search-tasks/1/resume"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("任务已正常完成，无法恢复"));
    }

    @Test
    void resumeReturnsBadRequestOnConcurrentClaimConflict() throws Exception {
        when(roundService.resume(1L)).thenThrow(new IllegalStateException("任务正在被其他执行器恢复，请稍后重试"));

        mockMvc.perform(post("/api/search-tasks/1/resume"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("任务正在被其他执行器恢复，请稍后重试"));
    }

    @Test
    void resumeReturnsBadRequestWhenActiveRunning() throws Exception {
        when(roundService.resume(1L)).thenThrow(new IllegalStateException("任务正在执行中，无法恢复"));

        mockMvc.perform(post("/api/search-tasks/1/resume"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("任务正在执行中，无法恢复"));
    }
}
