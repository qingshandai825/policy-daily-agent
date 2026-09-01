package com.itheima.policydailyagent.domain.report;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "report_section", indexes = {
        @Index(name = "idx_report_section_parent_sort", columnList = "parent_id,sort_order")
})
public class ReportSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "section_code", nullable = false, unique = true, length = 100)
    private String sectionCode;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "section_name", nullable = false, length = 200)
    private String sectionName;

    @Column(name = "section_level", nullable = false)
    private int sectionLevel;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "writing_guide", columnDefinition = "TEXT")
    private String writingGuide;

    @Column(name = "key_point_placeholder", length = 100)
    private String keyPointPlaceholder;

    @Column(name = "content_placeholder", length = 100)
    private String contentPlaceholder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
