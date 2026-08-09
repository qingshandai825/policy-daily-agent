package com.itheima.policydailyagent.agent.exception;

public class RetryableToolException extends RuntimeException {

    public RetryableToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
