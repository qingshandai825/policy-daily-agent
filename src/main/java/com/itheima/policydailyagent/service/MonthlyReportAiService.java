package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.entity.PolicyDocument;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class MonthlyReportAiService {

    private static final int MAX_DOCUMENT_COUNT = 5;

    private final ChatModel chatModel;

    public MonthlyReportAiService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 生成“本期要目”条目。
     *
     * 注意：
     * 这里只生成条目文字，不生成“·”“●”等项目符号。
     * 项目符号由 MonthlyReportService 统一添加，避免格式不一致。
     */
    public String generateKeyPoints(
            String sectionName,
            List<PolicyDocument> documents,
            String emptyText
    ) {
        if (documents == null || documents.isEmpty()) {
            return emptyText;
        }

        String prompt = """
                你是一名政府工作月报撰写助手。请根据给定材料，生成“本期要目”中的【%s】条目。

                写作要求：
                1. 只输出条目内容，不要输出栏目标题。
                2. 每条单独一行。
                3. 不要在条目前添加“·”“●”“-”“*”等项目符号。
                4. 每条控制在35字以内。
                5. 条目应突出政策文件、会议活动、专项行动、名单公示或重点工作名称。
                6. 只能依据材料，不得编造材料中没有的信息。
                7. 最多输出5条。
                8. 不要输出解释性文字，不要输出Markdown格式。

                输出示例：
                工业和信息化部印发XXXX通知
                国家数据局召开XXXX会议

                【材料】
                %s
                """.formatted(
                sectionName,
                buildPolicyMaterial(documents, 1200)
        );

        try {
            String result = chatModel.call(prompt);
            return normalizeKeyPoints(result, emptyText);
        } catch (Exception e) {
            return emptyText;
        }
    }

    /**
     * 生成“一、重点工作动态”下的“国家重点事项”正文。
     */
    public String generateNationalSection(
            List<PolicyDocument> documents,
            String emptyText
    ) {
        if (documents == null || documents.isEmpty()) {
            return emptyText;
        }

        String prompt = """
                你是一名政策研究和政府工作月报撰写助手。请根据给定材料，生成“国家重点事项”栏目正文。

                栏目定位：
                聚焦国家层面与人工智能赋能制造业相关的重大会议、重要讲话、政策文件、专项行动等内容，体现国家工作方向和政策信号。

                写作格式要求：
                1. 采用“标题+正文”形式。
                2. 每个事项必须分为两段：
                   第一段只写编号标题；
                   第二段只写正文。
                3. 标题格式必须为“1.标题”“2.标题”“3.标题”，标题后不要接正文。
                4. 标题应突出事项名称，不超过45字。
                5. 正文按照“时间/主体—核心内容—重点任务”的逻辑展开。
                6. 每条正文控制在180—320字。
                7. 不要输出“国家重点事项”栏目标题。
                8. 不要使用Markdown加粗符号，不要使用项目符号。
                9. 只能依据材料，不得编造材料中没有的信息。
                10. 如材料中没有明确时间、主体、任务或成效，应写“材料未明确”，不要自行补充。
                11. 不要输出“根据材料”“以下是”等解释性文字。

                输出格式必须严格类似下面这样：

                1.工业和信息化部印发XXXX通知
                6月X日，工业和信息化部印发XXXX。文件提出……重点部署……。

                2.国家数据局召开XXXX会议
                6月X日，国家数据局召开XXXX会议。会议强调……。

                【材料】
                %s
                """.formatted(
                buildPolicyMaterial(documents, 3000)
        );

        try {
            String result = chatModel.call(prompt);
            return normalizeSectionText(result, emptyText);
        } catch (Exception e) {
            return emptyText;
        }
    }

    /**
     * 生成“一、重点工作动态”下的“省内工作推进”正文。
     */
    public String generateProvincialSection(
            List<PolicyDocument> documents,
            String emptyText
    ) {
        if (documents == null || documents.isEmpty()) {
            return emptyText;
        }

        String prompt = """
                你是一名政府工作月报撰写助手。请根据给定材料，生成“省内工作推进”栏目正文。

                栏目定位：
                聚焦山东省内“人工智能+制造”工作的政策部署、会议培训、供需对接、产业链活动、能力中心建设、先锋应用培育等进展，体现省级层面的组织推进情况。

                写作格式要求：
                1. 采用“标题+正文”形式。
                2. 每个事项必须分为两段：
                   第一段只写编号标题；
                   第二段只写正文。
                3. 标题格式必须为“1.标题”“2.标题”“3.标题”，标题后不要接正文。
                4. 标题应突出工作事项名称，不超过45字。
                5. 正文按照“工作事项—推进情况—阶段成效”的逻辑展开。
                6. 每条正文控制在180—320字。
                7. 重点突出对全省人工智能赋能制造业的支撑作用。
                8. 不要输出“省内工作推进”栏目标题。
                9. 不要使用Markdown加粗符号，不要使用项目符号。
                10. 只能依据材料，不得编造材料中没有的信息。
                11. 如材料中没有明确推进情况、阶段成效或支撑作用，应写“材料未明确”，不要自行补充。
                12. 不要输出“根据材料”“以下是”等解释性文字。

                输出格式必须严格类似下面这样：

                1.山东省工业和信息化厅印发XXXX方案
                6月X日，山东省工业和信息化厅印发XXXX。方案围绕……提出……，对全省人工智能赋能制造业形成支撑。

                2.山东省组织开展XXXX活动
                6月X日，山东省组织开展XXXX活动。活动聚焦……推动……。

                【材料】
                %s
                """.formatted(
                buildPolicyMaterial(documents, 3000)
        );

        try {
            String result = chatModel.call(prompt);
            return normalizeSectionText(result, emptyText);
        } catch (Exception e) {
            return emptyText;
        }
    }

    /**
     * 将政策记录组织成可供模型使用的材料。
     */
    private String buildPolicyMaterial(
            List<PolicyDocument> documents,
            int maxContentLengthPerDocument
    ) {
        StringBuilder builder = new StringBuilder();

        int limit = Math.min(documents.size(), MAX_DOCUMENT_COUNT);

        for (int i = 0; i < limit; i++) {
            PolicyDocument document = documents.get(i);

            builder.append("【材料").append(i + 1).append("】\n");
            builder.append("标题：").append(safe(document.getTitle())).append("\n");
            builder.append("来源：").append(safe(document.getSourceName())).append("\n");
            builder.append("发布时间：")
                    .append(document.getPublishDate() == null
                            ? "材料未明确"
                            : document.getPublishDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日")))
                    .append("\n");
            builder.append("原文链接：").append(safe(document.getSourceUrl())).append("\n");

            if (hasText(document.getSummary())) {
                builder.append("已有摘要：")
                        .append(limitLength(document.getSummary(), maxContentLengthPerDocument))
                        .append("\n");
            }

            if (hasText(document.getContent())) {
                builder.append("正文摘录：")
                        .append(limitLength(document.getContent(), maxContentLengthPerDocument))
                        .append("\n");
            }

            builder.append("\n");
        }

        return builder.toString();
    }

    /**
     * 规范化“本期要目”输出。
     * 去掉模型可能生成的符号、序号、栏目标题。
     */
    private String normalizeKeyPoints(String text, String emptyText) {
        String cleaned = cleanAiText(text);

        if (!hasText(cleaned)) {
            return emptyText;
        }

        String[] lines = cleaned.replace("\r", "").split("\n");
        StringBuilder builder = new StringBuilder();

        int count = 0;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();

            if (!hasText(line)) {
                continue;
            }

            if (isNoiseOutputLine(line)) {
                continue;
            }

            line = line.replaceFirst("^[·•●]\\s*", "");
            line = line.replaceFirst("^[-*]\\s*", "");
            line = line.replaceFirst("^\\d+[\\.、．]\\s*", "");
            line = line.trim();

            if (!hasText(line)) {
                continue;
            }

            if (line.length() > 45) {
                line = line.substring(0, 45);
            }

            if (builder.length() > 0) {
                builder.append("\n");
            }

            builder.append(line);
            count++;

            if (count >= MAX_DOCUMENT_COUNT) {
                break;
            }
        }

        return builder.length() == 0 ? emptyText : builder.toString();
    }

    /**
     * 规范化正文栏目输出。
     * 保留“1.标题 + 正文”的结构，去除多余说明。
     */
    private String normalizeSectionText(String text, String emptyText) {
        String cleaned = cleanAiText(text);

        if (!hasText(cleaned)) {
            return emptyText;
        }

        String[] lines = cleaned.replace("\r", "").split("\n");
        StringBuilder builder = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();

            if (!hasText(line)) {
                if (builder.length() > 0 && !builder.toString().endsWith("\n\n")) {
                    builder.append("\n");
                }
                continue;
            }

            if (isNoiseOutputLine(line)) {
                continue;
            }

            line = line.replace("**", "").trim();

            if (builder.length() > 0) {
                builder.append("\n");
            }

            builder.append(line);
        }

        String result = builder.toString()
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return hasText(result) ? result : emptyText;
    }

    /**
     * 清理模型输出中的 Markdown 包裹、代码块和无关标记。
     */
    private String cleanAiText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("```markdown", "")
                .replace("```text", "")
                .replace("```", "")
                .replace("Markdown", "")
                .replace("markdown", "")
                .replace("\u00A0", " ")
                .replace("　　", "　　")
                .replace("\r", "\n")
                .trim();
    }

    /**
     * 过滤模型可能输出的解释性语句。
     */
    private boolean isNoiseOutputLine(String line) {
        if (!hasText(line)) {
            return true;
        }

        String compact = line.replace(" ", "");

        return compact.equals("国家重点事项")
                || compact.equals("省内工作推进")
                || compact.equals("本期要目")
                || compact.startsWith("以下是")
                || compact.startsWith("根据材料")
                || compact.startsWith("生成如下")
                || compact.startsWith("输出如下")
                || compact.startsWith("说明：");
    }

    private String limitLength(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        String cleaned = text
                .replace("\u00A0", " ")
                .replace("　", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.length() <= maxLength) {
            return cleaned;
        }

        return cleaned.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "材料未明确" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}