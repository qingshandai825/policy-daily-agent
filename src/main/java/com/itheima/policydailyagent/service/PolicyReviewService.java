package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.PolicyAnalysisStatus;
import com.itheima.policydailyagent.domain.policy.PolicyReview;
import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.dto.PolicyReviewRequest;
import com.itheima.policydailyagent.dto.PolicyReviewUpdateRequest;
import com.itheima.policydailyagent.dto.ReviewTaskSummary;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import com.itheima.policydailyagent.repository.PolicyReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PolicyReviewService {

    private final PolicyDocumentRepository policyDocumentRepository;
    private final PolicyReviewRepository policyReviewRepository;
    private final PolicyReviewStateMachine stateMachine;

    public PolicyReviewService(
            PolicyDocumentRepository policyDocumentRepository,
            PolicyReviewRepository policyReviewRepository,
            PolicyReviewStateMachine stateMachine
    ) {
        this.policyDocumentRepository = policyDocumentRepository;
        this.policyReviewRepository = policyReviewRepository;
        this.stateMachine = stateMachine;
    }

    @Transactional(readOnly = true)
    public List<PolicyDocument> listPoliciesForSearchTask(Long searchTaskId) {
        return policyDocumentRepository
                .findBySearchTaskIdOrderByPublishDateDescCreatedAtDesc(searchTaskId);
    }

    @Transactional(readOnly = true)
    public ReviewTaskSummary summarizeSearchTask(Long searchTaskId) {
        return new ReviewTaskSummary(
                searchTaskId,
                policyDocumentRepository.countBySearchTaskId(searchTaskId),
                count(searchTaskId, PolicyReviewStatus.PENDING),
                count(searchTaskId, PolicyReviewStatus.ACCEPTED),
                count(searchTaskId, PolicyReviewStatus.REJECTED)
        );
    }

    @Transactional(readOnly = true)
    public List<PolicyReview> listReviewHistory(Long policyId) {
        findPolicy(policyId);
        return policyReviewRepository.findByPolicyIdOrderByReviewedAtDesc(policyId);
    }

    @Transactional
    public PolicyDocument accept(Long policyId, PolicyReviewRequest request) {
        return transition(policyId, PolicyReviewStatus.ACCEPTED, request);
    }

    @Transactional
    public PolicyDocument reject(Long policyId, PolicyReviewRequest request) {
        return transition(policyId, PolicyReviewStatus.REJECTED, request);
    }

    @Transactional
    public PolicyDocument resetToPending(Long policyId, PolicyReviewRequest request) {
        return transition(policyId, PolicyReviewStatus.PENDING, request);
    }

    @Transactional
    public PolicyDocument updatePolicy(Long policyId, PolicyReviewUpdateRequest request) {
        PolicyDocument document = findPolicy(policyId);

        if (hasText(request.title())) {
            document.setTitle(request.title().trim());
        }
        if (hasText(request.sourceName())) {
            document.setSourceName(request.sourceName().trim());
        }
        if (request.publishDate() != null) {
            document.setPublishDate(request.publishDate());
        }
        if (hasText(request.category())) {
            document.setCategory(request.category().trim());
        }
        if (request.summary() != null) {
            document.setSummary(request.summary().trim());
        }
        if (request.reviewComment() != null) {
            document.setReviewComment(request.reviewComment().trim());
        }
        if (hasText(request.reviewedBy())) {
            document.setReviewedBy(request.reviewedBy().trim());
        }

        return policyDocumentRepository.save(document);
    }

    private PolicyDocument transition(
            Long policyId,
            PolicyReviewStatus target,
            PolicyReviewRequest request
    ) {
        PolicyDocument document = findPolicy(policyId);
        PolicyReviewStatus previous = document.getReviewStatus();
        stateMachine.validateTransition(previous, target);

        String reviewer = requireReviewer(request);
        String comment = request == null ? "" : safe(request.reviewComment());
        LocalDateTime reviewedAt = LocalDateTime.now();

        document.setReviewStatus(target);
        document.setReviewComment(comment);
        document.setReviewedBy(reviewer);
        document.setReviewedAt(reviewedAt);

        if (target == PolicyReviewStatus.ACCEPTED
                && (document.getAnalysisStatus() == PolicyAnalysisStatus.NOT_ANALYZED
                || document.getAnalysisStatus() == PolicyAnalysisStatus.FAILED)) {
            document.setAnalysisStatus(PolicyAnalysisStatus.READY);
        } else if (target != PolicyReviewStatus.ACCEPTED
                && document.getAnalysisStatus() == PolicyAnalysisStatus.READY) {
            document.setAnalysisStatus(PolicyAnalysisStatus.NOT_ANALYZED);
        }

        PolicyDocument saved = policyDocumentRepository.save(document);

        PolicyReview review = new PolicyReview();
        review.setPolicyId(policyId);
        review.setPreviousStatus(previous);
        review.setReviewStatus(target);
        review.setReviewedBy(reviewer);
        review.setReviewComment(comment);
        review.setReviewedAt(reviewedAt);
        policyReviewRepository.save(review);

        return saved;
    }

    private long count(Long searchTaskId, PolicyReviewStatus status) {
        return policyDocumentRepository.countBySearchTaskIdAndReviewStatus(searchTaskId, status);
    }

    private PolicyDocument findPolicy(Long policyId) {
        return policyDocumentRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("政策文档不存在，id=" + policyId));
    }

    private String requireReviewer(PolicyReviewRequest request) {
        if (request == null || !hasText(request.reviewedBy())) {
            throw new IllegalArgumentException("审核人不能为空");
        }
        return request.reviewedBy().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
