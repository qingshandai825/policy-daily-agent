package com.itheima.policydailyagent.agent.retry;

import org.springframework.stereotype.Component;

@Component
public class ThreadBackoffSleeper implements BackoffSleeper {

    @Override
    public void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Agent tool retry was interrupted", e);
        }
    }
}
