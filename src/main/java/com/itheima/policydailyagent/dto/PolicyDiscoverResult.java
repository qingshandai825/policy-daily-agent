package com.itheima.policydailyagent.dto;

import java.util.List;

public record PolicyDiscoverResult(

        Long taskId,

        int foundLinks,

        int savedCount,

        int duplicateCount,

        int filteredCount,

        int failedCount,


        List<String> savedTitles,

        List<String> duplicateUrls,

        List<String> filteredMessages,

        List<String> failedMessages
) {
}
