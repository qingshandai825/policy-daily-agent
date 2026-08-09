package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicySummaryService {

    private static final int MAX_CONTENT_LENGTH = 10000;

    private final ChatModel chatModel;
    private final PolicyDocumentRepository policyDocumentRepository;

    public PolicySummaryService(
            ChatModel chatModel,
            PolicyDocumentRepository policyDocumentRepository
    ) {
        this.chatModel = chatModel;
        this.policyDocumentRepository = policyDocumentRepository;
    }

    @Transactional
    public PolicyDocument summarizeById(Long id) {
        PolicyDocument document = policyDocumentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("政策文档不存在，id=" + id));

        if (document.getContent() == null || document.getContent().isBlank()) {
            throw new IllegalArgumentException("政策正文为空，无法生成摘要");
        }

        String prompt = buildPrompt(document);
        String summary = chatModel.call(prompt);

        document.setSummary(summary);
        document.setStatus("SUMMARIZED");

        return policyDocumentRepository.save(document);
    }

    private String buildPrompt(PolicyDocument document) {
        String content = document.getContent();

        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }

        return """
                你是一名政策研究助理，请根据给定政策原文生成结构化日报摘要。

                要求：
                1. 只能依据原文内容，不得编造原文没有的信息。
                2. 表述要正式、准确、简洁。
                3. 如果原文信息不足，请明确写“原文未明确”。
                4. 输出中文。
                5. 不要输出 Markdown 表格。

                请严格按照以下格式输出：

                【政策摘要】
                用150字以内概括政策主要内容。

                【重点内容】
                1. 
                2. 
                3. 

                【关注建议】
                结合政策内容，提出1—3条后续关注方向。不得脱离原文过度发挥。

                【政策信息】
                标题：%s
                来源：%s
                发布时间：%s

                【政策原文】
                %s
                """.formatted(
                safe(document.getTitle()),
                safe(document.getSourceName()),
                document.getPublishDate() == null ? "原文未明确" : document.getPublishDate().toString(),
                content
        );
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "原文未明确" : value;
    }
}