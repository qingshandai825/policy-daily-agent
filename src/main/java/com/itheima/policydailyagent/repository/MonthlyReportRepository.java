package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.report.MonthlyReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

    Optional<MonthlyReport> findByReportYearAndReportMonth(int reportYear, int reportMonth);

    List<MonthlyReport> findTop24ByOrderByReportYearDescReportMonthDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from MonthlyReport report where report.id = :reportId")
    Optional<MonthlyReport> findByIdForUpdate(@Param("reportId") Long reportId);
}
