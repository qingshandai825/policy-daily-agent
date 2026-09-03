package com.itheima.policydailyagent.service.search;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 纯函数化的主题覆盖度评估器。对每个主题统计命中的候选政策数：
 * 命中数 >= 阈值 → COVERED；0 < 命中数 < 阈值 → INSUFFICIENT；0 → NOT_COVERED。
 * 匹配范围限定为 title/keywords/summary/cleanedContent，并对正文扫描长度做限制，
 * 避免超大正文无界扫描。
 */
@Component
public class TopicCoverageEvaluator {

    public List<TopicCoverageResult> evaluate(
            List<CandidateText> candidates,
            List<String> topics,
            int coverageThreshold,
            int maxContentScanLength
    ) {
        List<CandidateText> safeCandidates = candidates == null ? List.of() : candidates;
        List<TopicCoverageResult> results = new ArrayList<>();
        if (topics == null || topics.isEmpty()) {
            return results;
        }
        for (String topic : topics) {
            String normalized = topic == null ? "" : topic.trim();
            List<Long> evidence = new ArrayList<>();
            for (CandidateText candidate : safeCandidates) {
                if (matches(candidate, normalized, maxContentScanLength)) {
                    evidence.add(candidate.policyId());
                }
            }
            CoverageStatus status;
            if (evidence.size() >= coverageThreshold) {
                status = CoverageStatus.COVERED;
            } else if (evidence.isEmpty()) {
                status = CoverageStatus.NOT_COVERED;
            } else {
                status = CoverageStatus.INSUFFICIENT;
            }
            results.add(new TopicCoverageResult(topic, evidence.size(), status, List.copyOf(evidence)));
        }
        return results;
    }

    private boolean matches(CandidateText candidate, String topic, int maxContentScanLength) {
        if (candidate == null || topic.isEmpty()) {
            return false;
        }
        String needle = topic.toLowerCase(Locale.ROOT);
        if (contains(candidate.title(), needle)) {
            return true;
        }
        if (contains(candidate.keywords(), needle)) {
            return true;
        }
        if (contains(candidate.summary(), needle)) {
            return true;
        }
        return containsLimited(candidate.content(), needle, maxContentScanLength);
    }

    private boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean containsLimited(String text, String needle, int maxLength) {
        if (text == null) {
            return false;
        }
        String limited = maxLength > 0 && text.length() > maxLength
                ? text.substring(0, maxLength)
                : text;
        return limited.toLowerCase(Locale.ROOT).contains(needle);
    }
}
