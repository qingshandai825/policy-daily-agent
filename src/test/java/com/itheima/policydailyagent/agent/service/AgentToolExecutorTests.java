package com.itheima.policydailyagent.agent.service;

import com.itheima.policydailyagent.agent.entity.AgentStep;
import com.itheima.policydailyagent.agent.model.AgentStage;
import com.itheima.policydailyagent.agent.model.ToolFailureType;
import com.itheima.policydailyagent.agent.retry.BackoffSleeper;
import com.itheima.policydailyagent.agent.retry.ToolFailureClassifier;
import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.agent.tool.AgentToolCall;
import com.itheima.policydailyagent.agent.tool.ToolRetryPolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolExecutorTests {

    private final AgentMemoryService memoryService = mock(AgentMemoryService.class);
    private final BackoffSleeper backoffSleeper = mock(BackoffSleeper.class);
    private final AgentToolExecutor executor = new AgentToolExecutor(
            memoryService,
            new ToolFailureClassifier(),
            backoffSleeper
    );

    @Test
    void shouldRetryTransientFailureAndPersistEachAttempt() {
        AgentStep firstStep = step(11L);
        AgentStep secondStep = step(12L);
        when(memoryService.startStep(any(), eq("test.network"), any(Integer.class), eq(3), any()))
                .thenReturn(firstStep, secondStep);

        AtomicInteger calls = new AtomicInteger();
        AgentTool<String, String> tool = new TestTool(input -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary", new IOException("connection reset"));
            }
            return "ok";
        });

        String result = executor.execute(
                new AgentToolCall(1L, 2L, AgentStage.CRAWLING),
                tool,
                "https://example.gov.cn/policy"
        );

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
        verify(memoryService).failStep(eq(11L), eq(ToolFailureType.RETRYABLE), any(), eq(true));
        verify(memoryService).finishStep(12L, "ok");
        verify(backoffSleeper).sleep(10L);
    }

    @Test
    void shouldNotRetryPermanentFailure() {
        AgentStep step = step(21L);
        when(memoryService.startStep(any(), eq("test.network"), any(Integer.class), eq(3), any()))
                .thenReturn(step);

        AgentTool<String, String> tool = new TestTool(input -> {
            throw new IllegalArgumentException("invalid url");
        });

        assertThatThrownBy(() -> executor.execute(
                new AgentToolCall(1L, 2L, AgentStage.CRAWLING),
                tool,
                "bad-url"
        )).isInstanceOf(IllegalArgumentException.class);

        verify(memoryService).failStep(eq(21L), eq(ToolFailureType.PERMANENT), any(), eq(false));
        verify(backoffSleeper, never()).sleep(any(Long.class));
    }

    private AgentStep step(Long id) {
        AgentStep step = mock(AgentStep.class);
        when(step.getId()).thenReturn(id);
        return step;
    }

    private interface ToolAction {
        String execute(String input);
    }

    private record TestTool(ToolAction action) implements AgentTool<String, String> {

        @Override
        public String name() {
            return "test.network";
        }

        @Override
        public String description() {
            return "test tool";
        }

        @Override
        public String execute(String input) {
            return action.execute(input);
        }

        @Override
        public ToolRetryPolicy retryPolicy() {
            return new ToolRetryPolicy(3, Duration.ofMillis(10), 2.0, Duration.ofMillis(50));
        }
    }
}
