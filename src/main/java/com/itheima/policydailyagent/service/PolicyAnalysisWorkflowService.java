package com.itheima.policydailyagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.policydailyagent.domain.analysis.AnalysisRunStatus;
import com.itheima.policydailyagent.domain.analysis.PolicyAnalysis;
import com.itheima.policydailyagent.domain.policy.PolicyAnalysisStatus;
import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.domain.report.MonthlyReportItem;
import com.itheima.policydailyagent.domain.report.ReportSection;
import com.itheima.policydailyagent.dto.*;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.MonthlyReportItemRepository;
import com.itheima.policydailyagent.repository.PolicyAnalysisRepository;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.ReportSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class PolicyAnalysisWorkflowService {

    public static final String PROMPT_VERSION = "policy-analysis-v2-checked";

    private final PolicyDocumentRepository policyRepository;
    private final PolicyAnalysisRepository analysisRepository;
    private final ReportSectionRepository sectionRepository;
    private final MonthlyReportItemRepository reportItemRepository;
    private final PolicySectionRecommendationService recommendationService;
    private final PolicyAnalysisAgent analysisAgent;
    private final PolicyAttachmentPersistenceService attachmentPersistenceService;
    private final ObjectMapper objectMapper;

    public PolicyAnalysisWorkflowService(
            PolicyDocumentRepository policyRepository,
            PolicyAnalysisRepository analysisRepository,
            ReportSectionRepository sectionRepository,
            MonthlyReportItemRepository reportItemRepository,
            PolicySectionRecommendationService recommendationService,
            PolicyAnalysisAgent analysisAgent,
            PolicyAttachmentPersistenceService attachmentPersistenceService,
            ObjectMapper objectMapper
    ) {
        this.policyRepository = policyRepository;
        this.analysisRepository = analysisRepository;
        this.sectionRepository = sectionRepository;
        this.reportItemRepository = reportItemRepository;
        this.recommendationService = recommendationService;
        this.analysisAgent = analysisAgent;
        this.attachmentPersistenceService = attachmentPersistenceService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AcceptedPolicyView> listAccepted() {
        return policyRepository
                .findByReviewStatusOrderByPublishDateDescCreatedAtDesc(PolicyReviewStatus.ACCEPTED)
                .stream()
                .map(policy -> toAcceptedView(policy, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public AcceptedPolicyView getAccepted(Long policyId) {
        PolicyDocument policy = requireAccepted(policyId);
        return toAcceptedView(policy, true);
    }

    @Transactional(readOnly = true)
    public List<PolicyAnalysisView> listAnalyses(Long policyId) {
        requireAccepted(policyId);
        return analysisRepository.findByPolicyIdOrderByVersionNoDesc(policyId)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportSectionOption> listEligibleSections() {
        return eligibleSections().stream()
                .map(section -> new ReportSectionOption(
                        section.getId(),
                        section.getSectionCode(),
                        section.getSectionName(),
                        section.getSectionLevel(),
                        section.getSortOrder(),
                        section.getWritingGuide()
                ))
                .toList();
    }

    public PolicyAnalysisView analyze(Long policyId, PolicyAnalysisRequest request) {
        PolicyDocument policy = requireAccepted(policyId);
        if (policy.getContentCompleteness() == null
                || !policy.getContentCompleteness().isAnalyzable()) {
            String status = policy.getContentCompleteness() == null
                    ? "UNKNOWN"
                    : policy.getContentCompleteness().name();
            throw new IllegalArgumentException(
                    "政策正文完整性为 " + status + "，请先重新抓取并确保附件正文完整后再分析"
            );
        }
        String content = contentOf(policy);
        if (content.isBlank()) {
            throw new IllegalArgumentException("政策正文为空，无法进行 Agent 分析");
        }
        if (!analysisAgent.isAvailable()) {
            throw new AgentUnavailableException(
                    "Agent 未启用，请配置 POLICY_AGENT_MODEL=deepseek 和 DEEPSEEK_API_KEY 后重启服务"
            );
        }

        List<ReportSection> sections = eligibleSections();
        var ruleRecommendation = recommendationService.recommend(policy, sections);
        int nextVersion = analysisRepository.findTopByPolicyIdOrderByVersionNoDesc(policyId)
                .map(latest -> latest.getVersionNo() + 1)
                .orElse(1);

        PolicyAnalysis analysis = new PolicyAnalysis();
        analysis.setPolicyId(policyId);
        analysis.setVersionNo(nextVersion);
        analysis.setRunStatus(AnalysisRunStatus.RUNNING);
        analysis.setInputHash(sha256(content));
        analysis.setModelName(analysisAgent.modelName());
        analysis.setPromptVersion(PROMPT_VERSION);
        analysis.setCreatedBy(request.createdBy().trim());
        analysis = analysisRepository.save(analysis);

        policy.setAnalysisStatus(PolicyAnalysisStatus.ANALYZING);
        policyRepository.save(policy);

        try {
            PolicyAnalysisDraft draft = analysisAgent.analyze(policy, sections, ruleRecommendation);
            analysis.setBasicInfoJson(objectMapper.writeValueAsString(draft.basicInfo()));
            analysis.setCoreContent(draft.coreContent());
            analysis.setRelevantContent(draft.relevantContent());
            analysis.setRecommendedSectionCode(draft.recommendedSectionCode());
            analysis.setRecommendationReason(draft.recommendationReason());
            analysis.setGeneratedTitle(draft.generatedTitle());
            analysis.setGeneratedContent(draft.generatedContent());
            analysis.setEvidenceJson(objectMapper.writeValueAsString(draft.evidence()));
            analysis.setQualityReportJson(objectMapper.writeValueAsString(draft.qualityReport()));
            analysis.setRunStatus(AnalysisRunStatus.SUCCEEDED);
            analysis.setCompletedAt(LocalDateTime.now());
            analysis = analysisRepository.save(analysis);

            policy.setAnalysisStatus(PolicyAnalysisStatus.ANALYZED);
            policyRepository.save(policy);
            return toView(analysis);
        } catch (Exception e) {
            if (e instanceof DraftValidationException validation) {
                try {
                    analysis.setQualityReportJson(objectMapper.writeValueAsString(validation.report()));
                } catch (JsonProcessingException ignored) {
                    // 质量报告是只读诊断信息，不替代失败状态与审核门。
                }
            }
            analysis.setRunStatus(AnalysisRunStatus.FAILED);
            analysis.setErrorMessage(limitError(e.getMessage()));
            analysis.setCompletedAt(LocalDateTime.now());
            analysisRepository.save(analysis);

            policy.setAnalysisStatus(PolicyAnalysisStatus.FAILED);
            policyRepository.save(policy);
            throw new PolicyAnalysisExecutionException("Agent 分析失败：" + limitError(e.getMessage()), e);
        }
    }

    @Transactional(readOnly = true)
    public PolicyAnalysis requireSucceededAnalysis(Long policyId, Long analysisId) {
        PolicyAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("分析记录不存在，id=" + analysisId));
        if (!analysis.getPolicyId().equals(policyId)) {
            throw new IllegalArgumentException("分析记录不属于指定政策");
        }
        if (analysis.getRunStatus() != AnalysisRunStatus.SUCCEEDED) {
            throw new IllegalArgumentException("只有 SUCCEEDED 的分析结果可以进入月报内容池");
        }
        return analysis;
    }

    private AcceptedPolicyView toAcceptedView(PolicyDocument policy, boolean includeAllAnalyses) {
        List<PolicyAnalysisView> analyses = analysisRepository
                .findByPolicyIdOrderByVersionNoDesc(policy.getId())
                .stream()
                .map(this::toView)
                .toList();
        PolicyAnalysisView latest = analyses.isEmpty() ? null : analyses.get(0);
        return new AcceptedPolicyView(
                policy.getId(),
                policy.getTitle(),
                policy.getSourceName(),
                policy.getPublishDate(),
                policy.getSourceUrl(),
                policy.getPolicyType(),
                policy.getCategory(),
                policy.getKeywords(),
                policy.getSummary(),
                contentOf(policy),
                policy.getContentCompleteness(),
                policy.getContentQualityReason(),
                policy.getAttachmentCount(),
                policy.getExtractedAttachmentCount(),
                policy.getContentRefreshedAt(),
                includeAllAnalyses
                        ? attachmentPersistenceService.listViews(policy.getId())
                        : List.of(),
                policy.getAnalysisStatus(),
                policy.getReviewedBy(),
                policy.getReviewedAt(),
                latest,
                includeAllAnalyses ? analyses : List.of()
        );
    }

    private PolicyAnalysisView toView(PolicyAnalysis analysis) {
        List<ReportPlacementView> placements = reportItemRepository
                .findByAnalysisIdOrderByCreatedAtDesc(analysis.getId())
                .stream()
                .map(this::toPlacement)
                .toList();
        return new PolicyAnalysisView(
                analysis.getId(),
                analysis.getPolicyId(),
                analysis.getVersionNo(),
                analysis.getRunStatus(),
                analysis.getModelName(),
                analysis.getPromptVersion(),
                readBasicInfo(analysis.getBasicInfoJson()),
                analysis.getCoreContent(),
                analysis.getRelevantContent(),
                analysis.getRecommendedSectionCode(),
                analysis.getRecommendationReason(),
                analysis.getGeneratedTitle(),
                analysis.getGeneratedContent(),
                readEvidence(analysis.getEvidenceJson()),
                analysis.getErrorMessage(),
                analysis.getCreatedBy(),
                analysis.getCreatedAt(),
                analysis.getCompletedAt(),
                placements,
                readQualityReport(analysis.getQualityReportJson())
        );
    }

    private ReportPlacementView toPlacement(MonthlyReportItem item) {
        return new ReportPlacementView(
                item.getReportId(),
                item.getId(),
                item.getSectionId(),
                item.getStatus()
        );
    }

    private com.itheima.policydailyagent.dto.DraftQualityReport readQualityReport(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, com.itheima.policydailyagent.dto.DraftQualityReport.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private PolicyBasicInfoView readBasicInfo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, PolicyBasicInfoView.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<String> readEvidence(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<ReportSection> eligibleSections() {
        List<ReportSection> sections = sectionRepository.findByActiveTrueOrderBySortOrderAsc()
                .stream()
                .filter(section -> section.getContentPlaceholder() != null
                        && !section.getContentPlaceholder().isBlank())
                .toList();
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("模板中没有配置可写入的内容栏目");
        }
        return sections;
    }

    private PolicyDocument requireAccepted(Long policyId) {
        PolicyDocument policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("政策文档不存在，id=" + policyId));
        if (policy.getReviewStatus() != PolicyReviewStatus.ACCEPTED) {
            throw new IllegalArgumentException("只有人工审核状态为 ACCEPTED 的政策才允许进入分析流程");
        }
        return policy;
    }

    private String contentOf(PolicyDocument policy) {
        String content = policy.getCleanedContent();
        if (content == null || content.isBlank()) {
            content = policy.getContent();
        }
        return content == null ? "" : content.trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 SHA-256", e);
        }
    }

    private String limitError(String value) {
        String message = value == null || value.isBlank() ? "未知错误" : value.trim();
        return message.length() <= 1800 ? message : message.substring(0, 1800);
    }
}
