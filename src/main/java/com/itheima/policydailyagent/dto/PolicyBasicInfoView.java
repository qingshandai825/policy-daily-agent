package com.itheima.policydailyagent.dto;

public record PolicyBasicInfoView(
        String policyName,
        String issuingAuthority,
        String publishDate,
        String policyBackground
) {
}
