package com.itheima.policydailyagent.dto;

public record DateFilterResult(
        boolean accepted,
        String status,
        String reason
) {
}
