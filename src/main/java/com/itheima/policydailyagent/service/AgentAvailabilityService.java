package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.AgentStatusView;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgentAvailabilityService {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final String modelName;

    public AgentAvailabilityService(
            ObjectProvider<ChatModel> chatModelProvider,
            @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}") String modelName
    ) {
        this.chatModelProvider = chatModelProvider;
        this.modelName = modelName;
    }

    public AgentStatusView status() {
        boolean available = chatModelProvider.getIfAvailable() != null;
        return new AgentStatusView(
                available,
                available ? "DeepSeek" : "未启用",
                available ? modelName : null,
                available
                        ? "Agent 已就绪"
                        : "系统其余功能可正常使用；启用 Agent 请同时配置 POLICY_AGENT_MODEL=deepseek 和 DEEPSEEK_API_KEY"
        );
    }

    public boolean isAvailable() {
        return chatModelProvider.getIfAvailable() != null;
    }

    public ChatModel requireChatModel() {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new AgentUnavailableException(
                    "Agent 未启用，请配置 POLICY_AGENT_MODEL=deepseek 和 DEEPSEEK_API_KEY 后重启服务"
            );
        }
        return chatModel;
    }

    public String modelName() {
        return modelName;
    }
}
