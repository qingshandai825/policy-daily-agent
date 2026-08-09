package com.itheima.policydailyagent.agent.retry;

@FunctionalInterface
public interface BackoffSleeper {

    void sleep(long millis);
}
