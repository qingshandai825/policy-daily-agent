package com.itheima.policydailyagent.dto;

import java.util.List;

public record PolicySearchRequest(
        List<String> keywords,
        List<String> sites,
        Integer maxResults,
        Boolean autoSave
) {
}
