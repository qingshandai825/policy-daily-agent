package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.report.MonthlyReportItemRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyReportItemRevisionRepository extends JpaRepository<MonthlyReportItemRevision, Long> {

    List<MonthlyReportItemRevision> findByReportItemIdOrderByRevisionNoDesc(Long reportItemId);

    Optional<MonthlyReportItemRevision> findTopByReportItemIdOrderByRevisionNoDesc(Long reportItemId);
}
