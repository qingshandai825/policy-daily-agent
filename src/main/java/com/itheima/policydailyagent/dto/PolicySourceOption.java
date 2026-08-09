package com.itheima.policydailyagent.dto;

import java.util.List;

public record PolicySourceOption(
        String id,
        String name,
        String level,
        String region,
        String type,
        String url,
        boolean directCrawl,
        List<String> keywords
) {
}
