package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.report.ReportSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportSectionRepository extends JpaRepository<ReportSection, Long> {

    List<ReportSection> findByActiveTrueOrderBySortOrderAsc();

    Optional<ReportSection> findBySectionCode(String sectionCode);
}
