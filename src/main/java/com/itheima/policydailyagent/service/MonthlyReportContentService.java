package com.itheima.policydailyagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.agent.dto.MonthlyPioneerContent;
import com.itheima.policydailyagent.agent.dto.MonthlyReportContent;
import com.itheima.policydailyagent.agent.dto.MonthlyReportItem;
import com.itheima.policydailyagent.agent.dto.MonthlyReportSection;
import com.itheima.policydailyagent.agent.exception.RetryableToolException;
import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MonthlyReportContentService {

    private static final int MAX_DOCUMENT_COUNT = 10;
    private static final int MAX_MATERIAL_LENGTH = 2200;
    private static final Pattern CHINESE_MONTH = Pattern.compile("(\\d{4})年(\\d{1,2})月");

    private final PolicyDocumentRepository policyDocumentRepository;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public MonthlyReportContentService(
            PolicyDocumentRepository policyDocumentRepository,
            ChatModel chatModel,
            ObjectMapper objectMapper
    ) {
        this.policyDocumentRepository = policyDocumentRepository;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public MonthlyReportContent generate(MonthlyReportGenerateRequest request) {
        MonthlyReportGenerateRequest safeRequest = request == null
                ? new MonthlyReportGenerateRequest(null, null, null, null, null, null, null, null)
                : request;
        YearMonth month = resolveMonth(safeRequest);
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        List<PolicyDocument> documents = safeRequest.taskId() == null
                ? policyDocumentRepository
                        .findByPublishDateBetweenAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                                startDate,
                                endDate,
                                PolicyReviewService.APPROVED
                        )
                : policyDocumentRepository
                        .findByDailyTaskIdAndReviewStatusOrderByPublishDateDescCreatedAtDesc(
                                safeRequest.taskId(),
                                PolicyReviewService.APPROVED
                        );

        ModelContent generated = documents.isEmpty()
                ? ModelContent.empty()
                : callModel(documents, month);

        return new MonthlyReportContent(
                month.getYear(),
                positiveOrDefault(safeRequest.issueNo(), 1),
                positiveOrDefault(safeRequest.totalIssueNo(), positiveOrDefault(safeRequest.issueNo(), 1)),
                month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                resolveStatDate(safeRequest.statDate(), endDate),
                safe(safeRequest.reportTo()),
                safe(safeRequest.sendTo()),
                safe(safeRequest.contactInfo()),
                normalizeSection(generated.national(), 5, "本期暂无国家重点事项。"),
                normalizeSection(generated.provincial(), 5, "本期暂无省内工作推进事项。"),
                normalizePioneer(generated.pioneer()),
                normalizeSection(generated.cases(), 2, "本期暂无经审核的典型应用案例。"),
                normalizeSection(generated.trends(), 3, "本期材料不足，暂不形成产业趋势判断。")
        );
    }

    private ModelContent callModel(List<PolicyDocument> documents, YearMonth month) {
        String prompt = """
                你是山东省人工智能赋能制造业工作月报的内容生成 Agent。
                请严格依据给定的、已经人工审核通过的材料生成月报内容，不得编造数字、成效、时间、主体或政策要求。
                材料没有明确的信息必须写“材料未明确”。

                报告月份：%s

                只输出一个合法 JSON 对象，不要输出 Markdown 代码块和解释。结构必须为：
                {
                  "national": {"keyPoints":["..."], "items":[{"title":"...","body":"..."}]},
                  "provincial": {"keyPoints":["..."], "items":[{"title":"...","body":"..."}]},
                  "pioneer": {
                    "keyPoints":["..."],
                    "overview":"...",
                    "metrics":"...",
                    "problems":"...",
                    "practices":"..."
                  },
                  "cases": {"keyPoints":["..."], "items":[{"title":"...","body":"..."}]},
                  "trends": {"keyPoints":["..."], "items":[{"title":"...","body":"..."}]}
                }

                内容约束：
                1. national 和 provincial 每栏最多5项，标题不超过45字，正文180至320字。
                2. cases 最多2项，只有材料中存在明确应用主体、场景和成效时才可生成。
                3. trends 最多3项，趋势判断必须能从多条材料中得到支撑，不得脱离材料发挥。
                4. pioneer 的数字、完成率、问题和经验只能来自材料；缺失时写“材料未明确”。
                5. keyPoints 每条不超过35字，不带序号或项目符号。
                6. items.title 不带数字序号，items.body 不使用 Markdown。

                审核材料：
                %s
                """.formatted(
                month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                buildMaterial(documents)
        );

        try {
            String response = chatModel.call(prompt);
            return objectMapper.readValue(extractJson(response), ModelContent.class);
        } catch (Exception error) {
            throw new RetryableToolException("月报内容模型输出无法解析", error);
        }
    }

    private String buildMaterial(List<PolicyDocument> documents) {
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(documents.size(), MAX_DOCUMENT_COUNT);
        for (int i = 0; i < limit; i++) {
            PolicyDocument document = documents.get(i);
            builder.append("【材料").append(i + 1).append("】\n")
                    .append("标题：").append(safe(document.getTitle())).append('\n')
                    .append("来源：").append(safe(document.getSourceName())).append('\n')
                    .append("发布日期：").append(document.getPublishDate()).append('\n')
                    .append("类别：").append(safe(document.getCategory())).append('\n')
                    .append("原文链接：").append(safe(document.getSourceUrl())).append('\n')
                    .append("摘要或证据：").append(materialText(document)).append("\n\n");
        }
        return builder.toString();
    }

    private String materialText(PolicyDocument document) {
        String value = firstNonBlank(
                document.getSummary(),
                document.getEvidenceSnippet(),
                document.getContent()
        );
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_MATERIAL_LENGTH
                ? normalized
                : normalized.substring(0, MAX_MATERIAL_LENGTH);
    }

    private MonthlyReportSection normalizeSection(ModelSection source, int maxItems, String fallback) {
        ModelSection safeSource = source == null ? ModelSection.empty() : source;
        List<String> keyPoints = cleanStrings(safeSource.keyPoints(), 5, 45);
        List<MonthlyReportItem> items = new ArrayList<>();

        if (safeSource.items() != null) {
            for (ModelItem item : safeSource.items()) {
                if (item == null || !hasText(item.title()) || !hasText(item.body())) {
                    continue;
                }
                items.add(new MonthlyReportItem(
                        clean(item.title(), 60).replaceFirst("^\\d+[.、．]\\s*", ""),
                        clean(item.body(), 1200)
                ));
                if (items.size() >= maxItems) {
                    break;
                }
            }
        }

        if (keyPoints.isEmpty()) {
            keyPoints = List.of(fallback);
        }
        if (items.isEmpty()) {
            items = List.of(new MonthlyReportItem("本期情况", fallback));
        }
        return new MonthlyReportSection(keyPoints, items);
    }

    private MonthlyPioneerContent normalizePioneer(ModelPioneer source) {
        ModelPioneer value = source == null ? ModelPioneer.empty() : source;
        List<String> keyPoints = cleanStrings(value.keyPoints(), 5, 45);
        if (keyPoints.isEmpty()) {
            keyPoints = List.of("本期先锋应用进展材料未明确");
        }
        return new MonthlyPioneerContent(
                keyPoints,
                fallback(value.overview(), "本期先锋应用总体推进情况材料未明确。"),
                fallback(value.metrics(), "本期重点指标完成情况材料未明确。"),
                fallback(value.problems(), "本期问题梳理和帮扶情况材料未明确。"),
                fallback(value.practices(), "本期典型经验和创新做法材料未明确。")
        );
    }

    private List<String> cleanStrings(List<String> source, int maxItems, int maxLength) {
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(this::hasText)
                .map(value -> clean(value, maxLength)
                        .replaceFirst("^[·•●*-]\\s*", "")
                        .replaceFirst("^\\d+[.、．]\\s*", ""))
                .filter(this::hasText)
                .limit(maxItems)
                .toList();
    }

    private YearMonth resolveMonth(MonthlyReportGenerateRequest request) {
        if (hasText(request.reportMonth())) {
            String value = request.reportMonth().trim();
            Matcher matcher = CHINESE_MONTH.matcher(value);
            if (matcher.find()) {
                return YearMonth.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            }
            try {
                return YearMonth.parse(value);
            } catch (DateTimeParseException ignored) {
                // Fall through to explicit year/current month.
            }
        }
        int year = request.reportYear() == null ? YearMonth.now().getYear() : request.reportYear();
        return YearMonth.of(year, YearMonth.now().getMonthValue());
    }

    private String resolveStatDate(String value, LocalDate fallback) {
        if (!hasText(value)) {
            return fallback.format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        }
        try {
            return LocalDate.parse(value.trim()).format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        } catch (DateTimeParseException ignored) {
            return value.trim();
        }
    }

    private String extractJson(String response) {
        if (!hasText(response)) {
            throw new IllegalArgumentException("模型返回空内容");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型返回内容不是 JSON 对象");
        }
        return response.substring(start, end + 1);
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String fallback(String value, String fallback) {
        return hasText(value) ? clean(value, 1200) : fallback;
    }

    private String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("**", "").replaceAll("\\s+", " ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "材料未明确";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ModelContent(
            ModelSection national,
            ModelSection provincial,
            ModelPioneer pioneer,
            ModelSection cases,
            ModelSection trends
    ) {
        static ModelContent empty() {
            return new ModelContent(
                    ModelSection.empty(),
                    ModelSection.empty(),
                    ModelPioneer.empty(),
                    ModelSection.empty(),
                    ModelSection.empty()
            );
        }
    }

    private record ModelSection(List<String> keyPoints, List<ModelItem> items) {
        static ModelSection empty() {
            return new ModelSection(List.of(), List.of());
        }
    }

    private record ModelItem(String title, String body) {
    }

    private record ModelPioneer(
            List<String> keyPoints,
            String overview,
            String metrics,
            String problems,
            String practices
    ) {
        static ModelPioneer empty() {
            return new ModelPioneer(List.of(), "", "", "", "");
        }
    }
}
