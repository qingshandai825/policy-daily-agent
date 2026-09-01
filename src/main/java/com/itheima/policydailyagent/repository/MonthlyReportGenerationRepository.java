package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.report.MonthlyReportGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyReportGenerationRepository extends JpaRepository<MonthlyReportGeneration, Long> {

    List<MonthlyReportGeneration> findByReportIdOrderByGenerationNoDesc(Long reportId);

    Optional<MonthlyReportGeneration> findTopByReportIdOrderByGenerationNoDesc(Long reportId);

    Optional<MonthlyReportGeneration> findByIdAndReportId(Long id, Long reportId);
}
