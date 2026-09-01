package com.itheima.policydailyagent.domain.report;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "report_template")
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_version", nullable = false, unique = true, length = 50)
    private String templateVersion;

    @Column(name = "resource_path", nullable = false, length = 500)
    private String resourcePath;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
