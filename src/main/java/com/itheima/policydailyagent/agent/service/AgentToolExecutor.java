package com.itheima.policydailyagent.agent.service;

import com.itheima.policydailyagent.agent.entity.AgentStep;
import com.itheima.policydailyagent.agent.model.ToolFailureType;
import com.itheima.policydailyagent.agent.retry.BackoffSleeper;
import com.itheima.policydailyagent.agent.retry.ToolFailureClassifier;
import com.itheima.policydailyagent.agent.tool.AgentTool;
import com.itheima.policydailyagent.agent.tool.AgentToolCall;
import com.itheima.policydailyagent.agent.tool.ToolRetryPolicy;
import org.springframework.stereotype.Service;

@Service
public class AgentToolExecutor {

    private final AgentMemoryService memoryService;
    private final ToolFailureClassifier failureClassifier;
    private final BackoffSleeper backoffSleeper;

    public AgentToolExecutor(
            AgentMemoryService memoryService,
            ToolFailureClassifier failureClassifier,
            BackoffSleeper backoffSleeper
    ) {
        this.memoryService = memoryService;
        this.failureClassifier = failureClassifier;
        this.backoffSleeper = backoffSleeper;
    }

    public <I, O> O execute(AgentToolCall call, AgentTool<I, O> tool, I input) {
        ToolRetryPolicy retryPolicy = tool.retryPolicy();

        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            AgentStep step = memoryService.startStep(
                    call,
                    tool.name(),
                    attempt,
                    retryPolicy.maxAttempts(),
                    tool.summarizeInput(input)
            );

            try {
                O output = tool.execute(input);
                memoryService.finishStep(step.getId(), tool.summarizeOutput(output));
                return output;
            } catch (RuntimeException error) {
                ToolFailureType failureType = failureClassifier.classify(error);
                boolean willRetry = failureType == ToolFailureType.RETRYABLE
                        && attempt < retryPolicy.maxAttempts();
                memoryService.failStep(step.getId(), failureType, error, willRetry);

                if (!willRetry) {
                    throw propagate(error);
                }
                backoffSleeper.sleep(retryPolicy.backoffMillisAfterAttempt(attempt));
            }
        }

        throw new IllegalStateException("Agent tool retry loop ended unexpectedly: " + tool.name());
    }

    private RuntimeException propagate(Throwable error) {
        if (error instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(error.getMessage(), error);
    }
}
