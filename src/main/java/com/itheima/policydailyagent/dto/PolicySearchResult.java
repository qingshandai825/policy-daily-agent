package com.itheima.policydailyagent.dto;

import java.util.List;

public record PolicySearchResult(

        String query,

        int totalResults,

        int savedCount,

        int duplicateCount,

        int failedCount,

        int summarizedCount,

        List<SearchItem> items,

        List<String> failedMessages
) {
    public record SearchItem(
            String title,
            String url,
            String snippet
    ) {
    }
}