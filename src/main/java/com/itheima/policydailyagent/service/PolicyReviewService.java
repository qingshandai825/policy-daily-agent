package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.dto.PolicyReviewRequest;
import com.itheima.policydailyagent.dto.PolicyReviewUpdateRequest;
import com.itheima.policydailyagent.dto.PolicySelectionRequest;
import com.itheima.policydailyagent.dto.ReviewTaskSummary;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PolicyReviewService {

    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String NEEDS_EDIT = "NEEDS_EDIT";

    private final PolicyDocumentRepository policyDocumentRepository;

    public PolicyReviewService(PolicyDocumentRepository policyDocumentRepository) {
        this.policyDocumentRepository = policyDocumentRepository;
    }

    @Transactional(readOnly = true)
    public List<PolicyDocument> listPoliciesForTask(Long taskId) {
        return policyDocumentRepository.findByDailyTaskIdOrderByPublishDateDescCreatedAtDesc(taskId);
    }

    @Transactional(readOnly = true)
    public ReviewTaskSummary summarizeTask(Long taskId) {
        return new ReviewTaskSummary(
                taskId,
                policyDocumentRepository.countByDailyTaskId(taskId),
                policyDocumentRepository.countByDailyTaskIdAndReviewStatus(taskId, PENDING_REVIEW),
                policyDocumentRepository.countByDailyTaskIdAndReviewStatus(taskId, APPROVED),
                policyDocumentRepository.countByDailyTaskIdAndReviewStatus(taskId, REJECTED),
                policyDocumentRepository.countByDailyTaskIdAndReviewStatus(taskId, NEEDS_EDIT)
        );
    }

    @Transactional
    public ReviewTaskSummary updateSelection(Long taskId, PolicySelectionRequest request) {
        List<PolicyDocument> documents = listPoliciesForTask(taskId);
        Set<Long> selectedIds = new HashSet<>(
                request == null ? List.of() : request.policyIds()
        );
        Set<Long> taskPolicyIds = documents.stream()
                .map(PolicyDocument::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!taskPolicyIds.containsAll(selectedIds)) {
            throw new IllegalArgumentException("选择中包含不属于当前任务的政策");
        }

        String reviewer = request == null ? "" : safe(request.reviewedBy());
        LocalDateTime reviewedAt = LocalDateTime.now();
        for (PolicyDocument document : documents) {
            boolean selected = selectedIds.contains(document.getId());
            document.setReviewStatus(selected ? APPROVED : PENDING_REVIEW);
            document.setReviewedBy(selected ? reviewer : "");
            document.setReviewedAt(selected ? reviewedAt : null);
            if (!selected) {
                document.setReviewComment("");
            }
        }
        policyDocumentRepository.saveAll(documents);
        return summarizeTask(taskId);
    }

    @Transactional
    public PolicyDocument approve(Long policyId, PolicyReviewRequest request) {
        return markReviewed(policyId, APPROVED, request);
    }

    @Transactional
    public PolicyDocument reject(Long policyId, PolicyReviewRequest request) {
        return markReviewed(policyId, REJECTED, request);
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

        document.setReviewStatus(NEEDS_EDIT);
        document.setReviewComment(safe(request.reviewComment()));
        document.setReviewedBy(safe(request.reviewedBy()));
        document.setReviewedAt(LocalDateTime.now());

        return policyDocumentRepository.save(document);
    }

    private PolicyDocument markReviewed(Long policyId, String status, PolicyReviewRequest request) {
        PolicyDocument document = findPolicy(policyId);
        document.setReviewStatus(status);
        document.setReviewComment(request == null ? "" : safe(request.reviewComment()));
        document.setReviewedBy(request == null ? "" : safe(request.reviewedBy()));
        document.setReviewedAt(LocalDateTime.now());
        return policyDocumentRepository.save(document);
    }

    private PolicyDocument findPolicy(Long policyId) {
        return policyDocumentRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy document does not exist, id=" + policyId));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
