package com.itheima.policydailyagent.entity;

import com.itheima.policydailyagent.domain.policy.ContentCompleteness;
import com.itheima.policydailyagent.domain.policy.PolicyAnalysisStatus;
import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "policy_document",
        indexes = {
                @Index(name = "idx_policy_source_url", columnList = "source_url", unique = true),
                @Index(name = "idx_policy_publish_date", columnList = "publish_date")
        }
)
public class PolicyDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 政策标题
     */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /**
     * 发布单位或来源网站
     */
    @Column(name = "source_name", length = 200)
    private String sourceName;

    /**
     * 发布时间
     */
    @Column(name = "publish_date")
    private LocalDate publishDate;

    /**
     * 原文链接
     */
    @Column(name = "source_url", nullable = false, unique = true, length = 1000)
    private String sourceUrl;

    /**
     * 政策正文
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "cleaned_content", columnDefinition = "TEXT")
    private String cleanedContent;

    /**
     * 大模型生成的摘要
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /**
     * 政策分类，例如：人工智能、智能制造、工业互联网等
     */
    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "policy_type", length = 100)
    private String policyType;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "relevance_score", precision = 5, scale = 2)
    private BigDecimal relevanceScore;

    @Column(name = "search_task_id")
    private Long searchTaskId;

    @Column(name = "retrieved_at")
    private LocalDateTime retrievedAt;

    @Column(name = "source_domain", length = 200)
    private String sourceDomain;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "authority_level", length = 50)
    private String authorityLevel;

    @Column(name = "date_source", length = 100)
    private String dateSource;

    @Column(name = "date_text", length = 200)
    private String dateText;

    @Column(name = "date_confidence", length = 50)
    private String dateConfidence;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "evidence_snippet", columnDefinition = "TEXT")
    private String evidenceSnippet;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_completeness", nullable = false, length = 30)
    private ContentCompleteness contentCompleteness = ContentCompleteness.UNKNOWN;

    @Column(name = "content_quality_reason", length = 1000)
    private String contentQualityReason;

    @Column(name = "attachment_count", nullable = false)
    private Integer attachmentCount = 0;

    @Column(name = "extracted_attachment_count", nullable = false)
    private Integer extractedAttachmentCount = 0;

    @Column(name = "content_refreshed_at")
    private LocalDateTime contentRefreshedAt;

    @Column(name = "filter_status", length = 50)
    private String filterStatus;

    @Column(name = "filter_reason", length = 500)
    private String filterReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private PolicyReviewStatus reviewStatus = PolicyReviewStatus.PENDING;

    @Column(name = "review_comment", length = 1000)
    private String reviewComment;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 30)
    private PolicyAnalysisStatus analysisStatus = PolicyAnalysisStatus.NOT_ANALYZED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.reviewStatus == null) {
            this.reviewStatus = PolicyReviewStatus.PENDING;
        }
        if (this.analysisStatus == null) {
            this.analysisStatus = PolicyAnalysisStatus.NOT_ANALYZED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceName() {
        return sourceName;
    }

    public LocalDate getPublishDate() {
        return publishDate;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getContent() {
        return content;
    }

    public String getSummary() {
        return summary;
    }

    public String getCategory() {
        return category;
    }

    public PolicyAnalysisStatus getAnalysisStatus() {
        return analysisStatus;
    }

    public Long getSearchTaskId() {
        return searchTaskId;
    }

    public LocalDateTime getRetrievedAt() {
        return retrievedAt;
    }

    public String getSourceDomain() {
        return sourceDomain;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getAuthorityLevel() {
        return authorityLevel;
    }

    public String getDateSource() {
        return dateSource;
    }

    public String getDateText() {
        return dateText;
    }

    public String getDateConfidence() {
        return dateConfidence;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getEvidenceSnippet() {
        return evidenceSnippet;
    }

    public String getFilterStatus() {
        return filterStatus;
    }

    public String getFilterReason() {
        return filterReason;
    }

    public PolicyReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public void setPublishDate(LocalDate publishDate) {
        this.publishDate = publishDate;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setAnalysisStatus(PolicyAnalysisStatus analysisStatus) {
        this.analysisStatus = analysisStatus;
    }

    public void setSearchTaskId(Long searchTaskId) {
        this.searchTaskId = searchTaskId;
    }

    public void setRetrievedAt(LocalDateTime retrievedAt) {
        this.retrievedAt = retrievedAt;
    }

    public void setSourceDomain(String sourceDomain) {
        this.sourceDomain = sourceDomain;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public void setAuthorityLevel(String authorityLevel) {
        this.authorityLevel = authorityLevel;
    }

    public void setDateSource(String dateSource) {
        this.dateSource = dateSource;
    }

    public void setDateText(String dateText) {
        this.dateText = dateText;
    }

    public void setDateConfidence(String dateConfidence) {
        this.dateConfidence = dateConfidence;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public void setEvidenceSnippet(String evidenceSnippet) {
        this.evidenceSnippet = evidenceSnippet;
    }

    public void setFilterStatus(String filterStatus) {
        this.filterStatus = filterStatus;
    }

    public void setFilterReason(String filterReason) {
        this.filterReason = filterReason;
    }

    public void setReviewStatus(PolicyReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
