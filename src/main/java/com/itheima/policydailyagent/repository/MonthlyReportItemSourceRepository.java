package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.report.MonthlyReportItemSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyReportItemSourceRepository extends JpaRepository<MonthlyReportItemSource, Long> {

    List<MonthlyReportItemSource> findByReportItemIdOrderByIdAsc(Long reportItemId);
}
