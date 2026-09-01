package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.report.MonthlyReportItem;
import com.itheima.policydailyagent.domain.report.ReportItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyReportItemRepository extends JpaRepository<MonthlyReportItem, Long> {

    List<MonthlyReportItem> findByReportIdOrderBySectionIdAscSortOrderAsc(Long reportId);

    List<MonthlyReportItem> findByReportIdAndStatusOrderBySectionIdAscSortOrderAsc(
            Long reportId,
            ReportItemStatus status
    );

    List<MonthlyReportItem> findByReportIdAndSectionIdOrderBySortOrderAsc(
            Long reportId,
            Long sectionId
    );

    long countByReportIdAndStatus(Long reportId, ReportItemStatus status);

    boolean existsByReportIdAndPolicyId(Long reportId, Long policyId);

    Optional<MonthlyReportItem> findTopByReportIdAndSectionIdOrderBySortOrderDesc(
            Long reportId,
            Long sectionId
    );

    List<MonthlyReportItem> findByAnalysisIdOrderByCreatedAtDesc(Long analysisId);
}
