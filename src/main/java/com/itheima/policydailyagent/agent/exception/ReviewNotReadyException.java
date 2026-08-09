package com.itheima.policydailyagent.agent.exception;

public class ReviewNotReadyException extends IllegalStateException {

    public ReviewNotReadyException(String message) {
        super(message);
    }
}
