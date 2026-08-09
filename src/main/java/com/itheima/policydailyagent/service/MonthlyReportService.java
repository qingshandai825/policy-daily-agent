package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.agent.PolicyDailyOrchestrator;
import com.itheima.policydailyagent.dto.MonthlyReportGenerateRequest;
import org.springframework.stereotype.Service;

/**
 * Compatibility facade for callers that still inject the legacy monthly report service.
 */
@Service
public class MonthlyReportService {

    private final PolicyDailyOrchestrator policyDailyOrchestrator;

    public MonthlyReportService(PolicyDailyOrchestrator policyDailyOrchestrator) {
        this.policyDailyOrchestrator = policyDailyOrchestrator;
    }

    public byte[] generateMonthlyReport(MonthlyReportGenerateRequest request) {
        return policyDailyOrchestrator.generateMonthlyReport(request);
    }
}