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
                false,
                null,
                date,
                "日报任务"
        );

        assertThat(request.resolvedTargetStartDate()).isEqualTo(date);
        assertThat(request.resolvedTargetEndDate()).isEqualTo(date);
    }

    @Test
    void shouldLeaveDateRangeOpenWhenDatesAreMissing() {
        PolicyDiscoverRequest request = new PolicyDiscoverRequest(
                "https://example.gov.cn/list",
                List.of(),
                10,
                false,
                null,
                null,
                "日报任务"
        );

        assertThat(request.resolvedTargetStartDate()).isNull();
        assertThat(request.resolvedTargetEndDate()).isNull();
        assertThat(request.hasTargetDateRange()).isFalse();
    }
}
