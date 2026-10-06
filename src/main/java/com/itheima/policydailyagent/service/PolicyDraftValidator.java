package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.PolicyAnalysisDraft;
import com.itheima.policydailyagent.entity.PolicyDocument;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 确定性检查仅验证引用、数字、已知日期及格式，不宣称验证完整语义蕴含。 */
public class PolicyDraftValidator {
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?(?:[%％])?");
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{4})\\s*(?:年|[-/])\\s*(\\d{1,2})\\s*(?:月|[-/])\\s*(\\d{1,2})(?:日)?(?!\\d)");
    private static final Pattern QUANTITY = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(亿元|万元|元|个|家|项|座|套|吨)");
    private static final Pattern MARKDOWN = Pattern.compile("(?m)^\\s*(?:#{1,6}\\s|[-*]\\s|\\d+[.、]\\s)|```|\\*\\*");

    public List<String> check(PolicyAnalysisDraft draft, PolicyDocument policy) {
        List<String> issues = new ArrayList<>();
        String body = policy.getCleanedContent();
        if (body == null || body.isBlank()) body = policy.getContent();
        String source = safe(body);
        String sourceWithMetadata = source + " " + safe(policy.getTitle()) + " " + safe(policy.getSourceName());
        if (policy.getPublishDate() != null) sourceWithMetadata += " " + policy.getPublishDate();
        if (draft.evidence() == null || draft.evidence().isEmpty()) {
            issues.add("至少提供一条逐字原文证据");
        } else {
            for (int i = 0; i < draft.evidence().size(); i++) {
                String quote = compact(draft.evidence().get(i));
                if (quote.length() < 4 || !compact(source).contains(quote)) {
                    issues.add("第" + (i + 1) + "条证据不在原文中或过短，请重新摘录");
                }
            }
        }
        if (safe(draft.generatedTitle()).length() > 45) issues.add("事项标题超过45字");
        if (safe(draft.generatedContent()).isBlank()) issues.add("月报正文不能为空");
        if (safe(draft.generatedContent()).length() > 800) issues.add("月报正文超过800字上限");
        if (MARKDOWN.matcher(safe(draft.generatedContent())).find()) issues.add("正文不能带Markdown或列表编号");
        String generated = safe(draft.generatedTitle()) + " " + safe(draft.generatedContent())
                + " " + safe(draft.coreContent()) + " " + safe(draft.relevantContent());
        if (draft.basicInfo() != null) {
            generated += " " + safe(draft.basicInfo().policyBackground());
            if (policy.getPublishDate() != null
                    && !policy.getPublishDate().toString().equals(draft.basicInfo().publishDate())) {
                issues.add("基本信息发布日期必须使用已校验日期" + policy.getPublishDate());
            }
        }
        Set<String> allowed = numbers(sourceWithMetadata);
        for (String number : numbers(generated)) {
            if (!allowed.contains(number)) issues.add("草稿数字缺少原文或已校验元数据依据：" + number);
        }
        Set<String> sourceDates = dates(sourceWithMetadata);
        for (String date : dates(generated)) {
            if (!sourceDates.contains(date)) issues.add("草稿日期缺少原文或已校验元数据依据：" + date);
        }
        Set<String> sourceQuantities = quantities(sourceWithMetadata);
        for (String quantity : quantities(generated)) {
            if (!sourceQuantities.contains(quantity)) issues.add("草稿数量及单位缺少原文依据：" + quantity);
        }
        for (String completed : List.of("已建成", "已完成", "已实现")) {
            if (generated.contains(completed) && !source.contains(completed)) {
                issues.add("草稿出现原文未明确的完成状态：" + completed + "，请回查并按原文措辞修订");
            }
        }
        return List.copyOf(issues);
    }

    private Set<String> numbers(String value) {
        Set<String> result = new LinkedHashSet<>();
        var matcher = NUMBER.matcher(value);
        while (matcher.find()) {
            String token = matcher.group().replace('％', '%');
            boolean percent = token.endsWith("%");
            String numeric = percent ? token.substring(0, token.length() - 1) : token;
            result.add(new BigDecimal(numeric).stripTrailingZeros().toPlainString() + (percent ? "%" : ""));
        }
        return result;
    }

    private String safe(String value) { return value == null ? "" : value; }
    private String compact(String value) { return safe(value).replaceAll("\\s+", ""); }

    private Set<String> dates(String value) {
        Set<String> result = new LinkedHashSet<>();
        var matcher = DATE.matcher(value);
        while (matcher.find()) {
            try {
                result.add(LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))).toString());
            } catch (java.time.DateTimeException e) {
                result.add("非法日期:" + matcher.group());
            }
        }
        return result;
    }

    private Set<String> quantities(String value) {
        Set<String> result = new LinkedHashSet<>();
        var matcher = QUANTITY.matcher(value);
        while (matcher.find()) {
            result.add(new BigDecimal(matcher.group(1)).stripTrailingZeros().toPlainString() + matcher.group(2));
        }
        return result;
    }
}
