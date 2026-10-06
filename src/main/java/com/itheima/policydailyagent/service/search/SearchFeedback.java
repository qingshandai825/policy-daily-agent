package com.itheima.policydailyagent.service.search;

import java.util.List;

/** 搜索结果的语义标签、原文术语与材料充分性评估；执行和硬停止条件由程序控制。 */
public record SearchFeedback(
        String mode,
        String modelName,
        List<TopicCoverageResult> coverage,
        List<TermEvidence> terms,
        String note,
        MaterialAssessment materialAssessment
) {
    public SearchFeedback {
        coverage = coverage == null ? List.of() : List.copyOf(coverage);
        terms = terms == null ? List.of() : List.copyOf(terms);
    }

    /** 旧轮次JSON缺少充分性字段时保持可读，不推断为材料充分。 */
    public SearchFeedback(String mode, String modelName, List<TopicCoverageResult> coverage,
                          List<TermEvidence> terms, String note) {
        this(mode, modelName, coverage, terms, note, null);
    }

    public boolean canFinishByAssessment() {
        return "SEMANTIC".equals(mode) && materialAssessment != null
                && Boolean.TRUE.equals(materialAssessment.materialSufficient())
                && materialAssessment.validationIssues().isEmpty();
    }

    public record MaterialAssessment(Boolean materialSufficient, List<String> missingTopics,
                                     List<Long> evidencePolicyIds, String reason, List<String> validationIssues) {
        public MaterialAssessment {
            missingTopics = missingTopics == null ? List.of() : List.copyOf(missingTopics);
            evidencePolicyIds = evidencePolicyIds == null ? List.of() : List.copyOf(evidencePolicyIds);
            validationIssues = validationIssues == null ? List.of("缺少充分性校验记录") : List.copyOf(validationIssues);
        }
    }

    public record TermEvidence(String topic, String term, Long policyId, String quote) {}
}
