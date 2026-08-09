package com.itheima.policydailyagent.agent.tool;

public interface AgentTool<I, O> {

    String name();

    String description();

    O execute(I input);

    default ToolRetryPolicy retryPolicy() {
        return ToolRetryPolicy.noRetry();
    }

    default String summarizeInput(I input) {
        return abbreviate(String.valueOf(input));
    }

    default String summarizeOutput(O output) {
        return abbreviate(String.valueOf(output));
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "null";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000) + "...";
    }
}
