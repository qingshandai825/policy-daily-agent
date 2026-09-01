package com.itheima.policydailyagent.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDiscoverRequestTests {

    @Test
    void shouldUseEndDateAsStartDateWhenOnlyEndDateIsProvided() {
        LocalDate date = LocalDate.of(2026, 8, 9);
        PolicyDiscoverRequest request = new PolicyDiscoverRequest(
                "https://example.gov.cn/list",
                List.of("人工智能"),
                10,
                null,
                date,
                "月报候选检索",
                "2026-08"
        );

        assertThat(request.resolvedTargetStartDate()).isEqualTo(date);
        assertThat(request.resolvedTargetEndDate()).isEqualTo(date);
        assertThat(request.reportMonth()).isEqualTo("2026-08");
    }

    @Test
    void shouldUseTodayWhenDateRangeIsMissing() {
        PolicyDiscoverRequest request = new PolicyDiscoverRequest(
                "https://example.gov.cn/list",
                List.of(),
                10,
                null,
                null,
                "月报候选检索",
                null
        );

        assertThat(request.resolvedTargetStartDate()).isEqualTo(LocalDate.now());
        assertThat(request.resolvedTargetEndDate()).isEqualTo(LocalDate.now());
    }
}
