package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.analysis.PolicyAnalysis;
import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.domain.report.*;
import com.itheima.policydailyagent.dto.PolicyAnalysisConfirmRequest;
import com.itheima.policydailyagent.dto.PolicyAnalysisConfirmResult;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportContentPoolService {

    private final PolicyDocumentRepository policyRepository;
    private final PolicyAnalysisWorkflowService analysisWorkflowService;
    private final MonthlyReportRepository reportRepository;
    private final ReportSectionRepository sectionRepository;
    private final MonthlyReportItemRepository itemRepository;
    private final MonthlyReportItemRevisionRepository revisionRepository;
    private final MonthlyReportItemSourceRepository sourceRepository;

    public ReportContentPoolService(
            PolicyDocumentRepository policyRepository,
            PolicyAnalysisWorkflowService analysisWorkflowService,
            MonthlyReportRepository reportRepository,
            ReportSectionRepository sectionRepository,
            MonthlyReportItemRepository itemRepository,
            MonthlyReportItemRevisionRepository revisionRepository,
            MonthlyReportItemSourceRepository sourceRepository
    ) {
        this.policyRepository = policyRepository;
        this.analysisWorkflowService = analysisWorkflowService;
        this.reportRepository = reportRepository;
        this.sectionRepository = sectionRepository;
        this.itemRepository = itemRepository;
        this.revisionRepository = revisionRepository;
        this.sourceRepository = sourceRepository;
    }

    @Transactional
    public PolicyAnalysisConfirmResult confirm(
            Long policyId,
            Long analysisId,
            PolicyAnalysisConfirmRequest request
    ) {
        PolicyDocument policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("政策文档不存在，id=" + policyId));
        if (policy.getReviewStatus() != PolicyReviewStatus.ACCEPTED) {
            throw new IllegalArgumentException("只有已采纳政策可以进入月报内容池");
        }

        PolicyAnalysis analysis = analysisWorkflowService.requireSucceededAnalysis(policyId, analysisId);
        ReportSection section = sectionRepository.findBySectionCode(request.sectionCode())
                .orElseThrow(() -> new IllegalArgumentException("月报栏目不存在：" + request.sectionCode()));
        if (!section.isActive() || section.getContentPlaceholder() == null
                || section.getContentPlaceholder().isBlank()) {
            throw new IllegalArgumentException("该栏目是结构容器，不能直接写入月报内容：" + section.getSectionCode());
        }

        MonthlyReport report = reportRepository
                .findByReportYearAndReportMonth(request.reportYear(), request.reportMonth())
                .orElseGet(() -> reportRepository.save(newReport(request.reportYear(), request.reportMonth())));

        if (report.getStatus() != MonthlyReportStatus.DRAFT) {
            throw new IllegalArgumentException("该月报已经整月确认或生成，请先在月报编辑页重新打开编辑");
        }

        if (itemRepository.existsByReportIdAndPolicyId(report.getId(), policyId)) {
            throw new IllegalArgumentException("该政策已经进入所选月份的月报内容池，请在月报内容编辑页面修改");
        }

        MonthlyReportItem item = new MonthlyReportItem();
        item.setReportId(report.getId());
        item.setSectionId(section.getId());
        item.setPolicyId(policyId);
        item.setAnalysisId(analysisId);
        item.setSourceType(ReportSourceType.POLICY);
        item.setItemTitle(request.itemTitle().trim());
        item.setAgentDraft(analysis.getGeneratedContent());
        item.setFinalContent(request.finalContent().trim());
        item.setStatus(ReportItemStatus.CONFIRMED);
        item.setSortOrder(nextSortOrder(report.getId(), section.getId()));
        item.setCreatedBy(request.confirmedBy().trim());
        item.setUpdatedBy(request.confirmedBy().trim());
        item = itemRepository.save(item);

        MonthlyReportItemRevision revision = new MonthlyReportItemRevision();
        revision.setReportItemId(item.getId());
        revision.setRevisionNo(1);
        revision.setItemTitle(item.getItemTitle());
        revision.setSectionId(item.getSectionId());
        revision.setItemStatus(item.getStatus());
        revision.setSortOrder(item.getSortOrder());
        revision.setContent(item.getFinalContent());
        revision.setChangeType("ANALYSIS_CONFIRMED");
        revision.setChangeNote("人工确认 Agent 分析结果进入月报内容池");
        revision.setEditor(request.confirmedBy().trim());
        revisionRepository.save(revision);

        MonthlyReportItemSource source = new MonthlyReportItemSource();
        source.setReportItemId(item.getId());
        source.setSourceType(ReportSourceType.POLICY);
        source.setPolicyId(policyId);
        source.setSourceTitle(policy.getTitle());
        source.setSourceUrl(policy.getSourceUrl());
        source.setSourceContentSnapshot(contentOf(policy));
        sourceRepository.save(source);

        return new PolicyAnalysisConfirmResult(
                report.getId(),
                item.getId(),
                report.getTitle(),
                section.getSectionCode(),
                item.getStatus()
        );
    }

    private MonthlyReport newReport(int year, int month) {
        MonthlyReport report = new MonthlyReport();
        report.setReportYear(year);
        report.setReportMonth(month);
        report.setTitle(year + "年" + month + "月人工智能赋能制造业工作月报");
        report.setStatus(MonthlyReportStatus.DRAFT);
        return report;
    }

    private int nextSortOrder(Long reportId, Long sectionId) {
        return itemRepository
                .findTopByReportIdAndSectionIdOrderBySortOrderDesc(reportId, sectionId)
                .map(item -> item.getSortOrder() + 10)
                .orElse(10);
    }

    private String contentOf(PolicyDocument policy) {
        String value = policy.getCleanedContent();
        if (value == null || value.isBlank()) {
            value = policy.getContent();
        }
        return value;
    }
}
