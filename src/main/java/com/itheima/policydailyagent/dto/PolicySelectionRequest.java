package com.itheima.policydailyagent.dto;

import java.util.List;

public record PolicySelectionRequest(
        List<Long> policyIds,
        String reviewedBy
) {
    public PolicySelectionRequest {
        policyIds = policyIds == null ? List.of() : List.copyOf(policyIds);
    }
}
