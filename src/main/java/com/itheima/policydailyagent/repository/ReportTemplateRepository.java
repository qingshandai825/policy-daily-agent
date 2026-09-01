package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.report.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {

    Optional<ReportTemplate> findByActiveTrue();

    Optional<ReportTemplate> findByTemplateVersion(String templateVersion);
}
