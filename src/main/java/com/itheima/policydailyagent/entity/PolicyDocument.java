package com.itheima.policydailyagent.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    /**
     * 处理状态：RAW、SUMMARIZED、WRITTEN
     */
    @Column(name = "daily_task_id")
    private Long dailyTaskId;

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

    @Column(name = "filter_status", length = 50)
    private String filterStatus;

    @Column(name = "filter_reason", length = 500)
    private String filterReason;

    @Column(name = "review_status", length = 50)
    private String reviewStatus = "PENDING_REVIEW";

    @Column(name = "review_comment", length = 1000)
    private String reviewComment;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "RAW";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "RAW";
        }
        if (this.reviewStatus == null) {
            this.reviewStatus = "PENDING_REVIEW";
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

    public String getStatus() {
        return status;
    }

    public Long getDailyTaskId() {
        return dailyTaskId;
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

    public String getReviewStatus() {
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

    public void setStatus(String status) {
        this.status = status;
    }

    public void setDailyTaskId(Long dailyTaskId) {
        this.dailyTaskId = dailyTaskId;
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

    public void setReviewStatus(String reviewStatus) {
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
