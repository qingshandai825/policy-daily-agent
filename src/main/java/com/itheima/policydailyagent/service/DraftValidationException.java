package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.DraftQualityReport;

public class DraftValidationException extends IllegalArgumentException {
    private final DraftQualityReport report;

    public DraftValidationException(DraftQualityReport report) {
        super("草稿在限定修订次数内未通过程序检查："
                + String.join("；", report.attempts().get(report.attempts().size() - 1).issues()));
        this.report = report;
    }

    public DraftQualityReport report() { return report; }
}
