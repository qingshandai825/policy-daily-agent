package com.itheima.policydailyagent.service.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopicCoverageEvaluatorTests {

    private final TopicCoverageEvaluator evaluator = new TopicCoverageEvaluator();

    private static CandidateText candidate(long id, String title, String keywords, String summary, String content) {
        return new CandidateText(id, title, keywords, summary, content);
    }

    @Test
    void marksCoveredWhenMatchCountReachesThreshold() {
        List<CandidateText> candidates = List.of(
                candidate(1L, "人工智能发展规划", null, null, null),
                candidate(2L, "人工智能伦理规范", null, null, null)
        );
        List<TopicCoverageResult> results = evaluator.evaluate(candidates, List.of("人工智能"), 2, 2000);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(CoverageStatus.COVERED);
        assertThat(results.get(0).matchedCandidateCount()).isEqualTo(2);
        assertThat(results.get(0).evidencePolicyIds()).containsExactly(1L, 2L);
    }

    @Test
    void marksNotCoveredWhenNoMatch() {
        List<CandidateText> candidates = List.of(
                candidate(1L, "数据安全管理办法", null, null, null)
        );
        List<TopicCoverageResult> results = evaluator.evaluate(candidates, List.of("算力"), 1, 2000);

        assertThat(results.get(0).status()).isEqualTo(CoverageStatus.NOT_COVERED);
        assertThat(results.get(0).matchedCandidateCount()).isZero();
        assertThat(results.get(0).evidencePolicyIds()).isEmpty();
    }

    @Test
    void marksInsufficientWhenMatchCountBelowThreshold() {
        List<CandidateText> candidates = List.of(
                candidate(1L, "人工智能发展", null, null, null)
        );
        List<TopicCoverageResult> results = evaluator.evaluate(candidates, List.of("人工智能"), 2, 2000);

        assertThat(results.get(0).status()).isEqualTo(CoverageStatus.INSUFFICIENT);
        assertThat(results.get(0).matchedCandidateCount()).isEqualTo(1);
    }

    @Test
    void matchesOnKeywordsSummaryAndBody() {
        List<CandidateText> candidates = List.of(
                candidate(1L, "通知", "大模型", null, null),
                candidate(2L, "意见", null, "发展智能体", null),
                candidate(3L, "公告", null, null, "推动工业互联网建设")
        );
        assertThat(evaluator.evaluate(candidates, List.of("大模型"), 1, 2000).get(0).status())
                .isEqualTo(CoverageStatus.COVERED);
        assertThat(evaluator.evaluate(candidates, List.of("智能体"), 1, 2000).get(0).status())
                .isEqualTo(CoverageStatus.COVERED);
        assertThat(evaluator.evaluate(candidates, List.of("工业互联网"), 1, 2000).get(0).status())
                .isEqualTo(CoverageStatus.COVERED);
    }

    @Test
    void limitsBodyScanLength() {
        String longContent = "前文".repeat(500) + "算力"; // 匹配词在扫描长度之外
        List<CandidateText> candidates = List.of(
                candidate(1L, null, null, null, longContent)
        );
        // 扫描长度限制到 100，匹配词超出范围 → 未覆盖
        assertThat(evaluator.evaluate(candidates, List.of("算力"), 1, 100).get(0).status())
                .isEqualTo(CoverageStatus.NOT_COVERED);
        // 扫描长度足够 → 覆盖
        assertThat(evaluator.evaluate(candidates, List.of("算力"), 1, 2000).get(0).status())
                .isEqualTo(CoverageStatus.COVERED);
    }
}
