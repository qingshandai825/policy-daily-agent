package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.report.MonthlyReport;
import com.itheima.policydailyagent.dto.CreateMonthlyReportRequest;
import com.itheima.policydailyagent.dto.MonthlyReportWorkspace;
import com.itheima.policydailyagent.repository.MonthlyReportItemRepository;
import com.itheima.policydailyagent.repository.MonthlyReportRepository;
import com.itheima.policydailyagent.repository.ReportSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MonthlyReportWorkspaceService {

    private final MonthlyReportRepository monthlyReportRepository;
    private final ReportSectionRepository reportSectionRepository;
    private final MonthlyReportItemRepository monthlyReportItemRepository;

    public MonthlyReportWorkspaceService(
            MonthlyReportRepository monthlyReportRepository,
            ReportSectionRepository reportSectionRepository,
            MonthlyReportItemRepository monthlyReportItemRepository
    ) {
        this.monthlyReportRepository = monthlyReportRepository;
        this.reportSectionRepository = reportSectionRepository;
        this.monthlyReportItemRepository = monthlyReportItemRepository;
    }

    @Transactional
    public MonthlyReportWorkspace createOrGet(CreateMonthlyReportRequest request) {
        MonthlyReport report = monthlyReportRepository
                .findByReportYearAndReportMonth(request.reportYear(), request.reportMonth())
                .orElseGet(() -> monthlyReportRepository.save(newReport(request)));
        return buildWorkspace(report);
    }

    @Transactional(readOnly = true)
    public MonthlyReportWorkspace getWorkspace(Long reportId) {
        MonthlyReport report = monthlyReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("月报不存在，id=" + reportId));
        return buildWorkspace(report);
    }

    @Transactional(readOnly = true)
    public List<MonthlyReport> listRecent() {
        return monthlyReportRepository.findTop24ByOrderByReportYearDescReportMonthDesc();
    }

    private MonthlyReport newReport(CreateMonthlyReportRequest request) {
        MonthlyReport report = new MonthlyReport();
        report.setReportYear(request.reportYear());
        report.setReportMonth(request.reportMonth());
        report.setTitle(request.reportYear() + "年" + request.reportMonth() + "月人工智能赋能制造业工作月报");
        return report;
    }

    private MonthlyReportWorkspace buildWorkspace(MonthlyReport report) {
        return new MonthlyReportWorkspace(
                report,
                reportSectionRepository.findByActiveTrueOrderBySortOrderAsc(),
                monthlyReportItemRepository.findByReportIdOrderBySectionIdAscSortOrderAsc(report.getId())
        );
    }
}
