package com.itheima.policydailyagent.agent.tool;

import java.time.Duration;

public record ToolRetryPolicy(
        int maxAttempts,
        Duration initialBackoff,
        double multiplier,
        Duration maxBackoff
) {
    public ToolRetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        initialBackoff = initialBackoff == null ? Duration.ZERO : initialBackoff;
        maxBackoff = maxBackoff == null ? initialBackoff : maxBackoff;
        multiplier = multiplier < 1.0 ? 1.0 : multiplier;
    }

    public static ToolRetryPolicy noRetry() {
        return new ToolRetryPolicy(1, Duration.ZERO, 1.0, Duration.ZERO);
    }

    public static ToolRetryPolicy externalCall() {
        return new ToolRetryPolicy(3, Duration.ofMillis(500), 2.0, Duration.ofSeconds(3));
    }

    public long backoffMillisAfterAttempt(int attemptNumber) {
        if (initialBackoff.isZero() || attemptNumber < 1) {
            return 0L;
        }
        double scaled = initialBackoff.toMillis() * Math.pow(multiplier, attemptNumber - 1);
        return Math.min((long) scaled, maxBackoff.toMillis());
    }
}
