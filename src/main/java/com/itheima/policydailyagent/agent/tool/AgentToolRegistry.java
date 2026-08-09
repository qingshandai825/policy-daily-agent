package com.itheima.policydailyagent.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool<?, ?>> tools;

    public AgentToolRegistry(List<AgentTool<?, ?>> toolList) {
        Map<String, AgentTool<?, ?>> registered = new LinkedHashMap<>();
        for (AgentTool<?, ?> tool : toolList) {
            AgentTool<?, ?> previous = registered.put(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("Duplicate agent tool name: " + tool.name());
            }
        }
        this.tools = Map.copyOf(registered);
    }

    public <T extends AgentTool<?, ?>> T require(String name, Class<T> toolType) {
        AgentTool<?, ?> tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown agent tool: " + name);
        }
        if (!toolType.isInstance(tool)) {
            throw new IllegalStateException("Agent tool has unexpected type: " + name);
        }
        return toolType.cast(tool);
    }

    public List<AgentToolDescriptor> descriptors() {
        return tools.values().stream()
                .map(tool -> new AgentToolDescriptor(
                        tool.name(),
                        tool.description(),
                        tool.retryPolicy().maxAttempts()
                ))
                .toList();
    }
}
