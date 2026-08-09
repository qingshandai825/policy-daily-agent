package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.DateFilterResult;
import com.itheima.policydailyagent.dto.PolicyCrawlResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDateFilterServiceTests {

    private final PolicyDateFilterService service = new PolicyDateFilterService();

    @Test
    void acceptsPublishDateWithinTargetRange() {
        DateFilterResult result = service.filter(
                crawlResult(LocalDate.of(2026, 8, 8)),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo("ACCEPTED");
    }

    @Test
    void filtersPublishDateBeforeTargetRange() {
        DateFilterResult result = service.filter(
                crawlResult(LocalDate.of(2026, 7, 31)),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo("FILTERED_DATE_BEFORE_RANGE");
    }

    @Test
    void filtersUnknownPublishDateWhenTargetRangeIsConfigured() {
        DateFilterResult result = service.filter(
                crawlResult(null),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo("FILTERED_DATE_UNKNOWN");
    }

    private PolicyCrawlResult crawlResult(LocalDate publishDate) {
        return new PolicyCrawlResult(
                "title",
                "source",
                publishDate,
                "https://example.gov.cn/policy.html",
                "content",
                LocalDateTime.now(),
                "example.gov.cn",
                "GOVERNMENT",
                "LOCAL_OR_DEPARTMENT_AUTHORITY",
                publishDate == null ? "UNDETECTED" : "PAGE_METADATA_OR_TEXT",
                publishDate == null ? "" : publishDate.toString(),
                publishDate == null ? "NONE" : "MEDIUM",
                "hash",
                "evidence"
        );
    }
}
