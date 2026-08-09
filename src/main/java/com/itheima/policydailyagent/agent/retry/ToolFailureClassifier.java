package com.itheima.policydailyagent.agent.retry;

import com.itheima.policydailyagent.agent.exception.RetryableToolException;
import com.itheima.policydailyagent.agent.model.ToolFailureType;
import org.jsoup.HttpStatusException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

@Component
public class ToolFailureClassifier {

    public ToolFailureType classify(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof RetryableToolException) {
                return ToolFailureType.RETRYABLE;
            }
            if (current instanceof TransientAiException) {
                return ToolFailureType.RETRYABLE;
            }
            if (current instanceof NonTransientAiException || current instanceof IllegalArgumentException) {
                return ToolFailureType.PERMANENT;
            }
            if (current instanceof HttpStatusException httpError) {
                int status = httpError.getStatusCode();
                return status == 429 || status >= 500
                        ? ToolFailureType.RETRYABLE
                        : ToolFailureType.PERMANENT;
            }
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof ConnectException
                    || current instanceof IOException) {
                return ToolFailureType.RETRYABLE;
            }
            current = current.getCause();
        }
        return ToolFailureType.PERMANENT;
    }
}
