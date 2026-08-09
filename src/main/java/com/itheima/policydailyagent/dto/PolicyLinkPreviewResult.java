package com.itheima.policydailyagent.dto;

import java.util.List;

public record PolicyLinkPreviewResult(
        int totalLinks,
        List<LinkItem> links
) {
    public record LinkItem(
            String text,
            String url
    ) {
    }
}