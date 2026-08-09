package com.itheima.policydailyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.agent.dto.DailyReportSynthesis;
import com.itheima.policydailyagent.agent.exception.RetryableToolException;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DailyReportSynthesisService {

    private static final int MAX_DOCUMENTS = 15;
    private static final int MAX_MATERIAL_LENGTH = 2400;

    private final PolicyDocumentRepository policyDocumentRepository;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public DailyReportSynthesisService(
            PolicyDocumentRepository policyDocumentRepository,
            ChatModel chatModel,
            ObjectMapper objectMapper
    ) {
        this.policyDocumentRepository = policyDocumentRepository;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public DailyReportSynthesis synthesize(Long taskId) {
        List<PolicyDocument> documents = policyDocumentRepository
                .findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                        taskId,
                        PolicyReviewService.APPROVED
                );
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("请至少勾选一条政策素材");
        }

        String prompt = """
                你是政策日报内容综合 Agent。请严格依据人工勾选的政策材料，形成一段综合研判和3至6条本期要点。
                不得编造原文没有的主体、时间、数字、政策要求或结论。多份材料存在关联时可以归纳共同方向，无法确认时写“材料未明确”。

                只输出合法 JSON，不要输出 Markdown：
                {"overview":"300至600字的综合研判","keyPoints":["要点1","要点2"]}

                人工勾选材料：
                %s
                """.formatted(buildMaterial(documents));

        try {
            String response = chatModel.call(prompt);
            ModelOutput output = objectMapper.readValue(extractJson(response), ModelOutput.class);
            String overview = clean(output.overview(), 1800);
            List<String> keyPoints = output.keyPoints() == null ? List.of() : output.keyPoints().stream()
                    .filter(this::hasText)
                    .map(value -> clean(value, 100).replaceFirst("^[·•●*-]\\s*", ""))
                    .limit(6)
                    .toList();
            if (!hasText(overview) || keyPoints.isEmpty()) {
                throw new IllegalArgumentException("模型未返回完整的综合研判和要点");
            }
            return new DailyReportSynthesis(taskId, overview, keyPoints);
        } catch (Exception error) {
            throw new RetryableToolException("所选政策综合失败", error);
        }
    }

    private String buildMaterial(List<PolicyDocument> documents) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(documents.size(), MAX_DOCUMENTS);
        for (int i = 0; i < count; i++) {
            PolicyDocument document = documents.get(i);
            builder.append("【材料").append(i + 1).append("】\n")
                    .append("标题：").append(safe(document.getTitle())).append('\n')
                    .append("来源：").append(safe(document.getSourceName())).append('\n')
                    .append("发布日期：").append(document.getPublishDate()).append('\n')
                    .append("链接：").append(safe(document.getSourceUrl())).append('\n')
                    .append("正文或证据：").append(materialText(document)).append("\n\n");
        }
        return builder.toString();
    }

    private String materialText(PolicyDocument document) {
        String value = firstNonBlank(document.getContent(), document.getSummary(), document.getEvidenceSnippet());
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_MATERIAL_LENGTH
                ? normalized
                : normalized.substring(0, MAX_MATERIAL_LENGTH);
    }

    private String extractJson(String response) {
        if (!hasText(response)) {
            throw new IllegalArgumentException("模型返回空内容");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型返回内容不是 JSON");
        }
        return response.substring(start, end + 1);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "材料未明确";
    }

    private String clean(String value, int maxLength) {
        String cleaned = safe(value).replace("**", "").replaceAll("\\s+", " ");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ModelOutput(String overview, List<String> keyPoints) {
    }
}
