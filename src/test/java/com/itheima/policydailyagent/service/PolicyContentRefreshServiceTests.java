package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.AttachmentExtractionStatus;
import com.itheima.policydailyagent.domain.policy.ContentCompleteness;
import com.itheima.policydailyagent.domain.policy.PolicyAnalysisStatus;
import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import com.itheima.policydailyagent.dto.PolicyAttachmentCrawlResult;
import com.itheima.policydailyagent.dto.PolicyCrawlResult;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PolicyContentRefreshServiceTests {

    private final PolicyDocumentRepository policyRepository = mock(PolicyDocumentRepository.class);
    private final PolicyCrawlerService crawlerService = mock(PolicyCrawlerService.class);
    private final PolicyAttachmentPersistenceService persistenceService =
            mock(PolicyAttachmentPersistenceService.class);
    private final PolicyContentRefreshService service = new PolicyContentRefreshService(
            policyRepository,
            crawlerService,
            persistenceService
    );

    @Test
    void shouldRefreshAcceptedPolicyAndResetAnalysisWhenContentChanges() {
        PolicyDocument policy = new PolicyDocument();
        policy.setId(7L);
        policy.setSourceUrl("https://example.gov.cn/policy");
        policy.setReviewStatus(PolicyReviewStatus.ACCEPTED);
        policy.setAnalysisStatus(PolicyAnalysisStatus.ANALYZED);
        policy.setContentHash("old-hash");

        PolicyAttachmentCrawlResult attachment = new PolicyAttachmentCrawlResult(
                "行动方案.pdf",
                "https://example.gov.cn/action.pdf",
                "PDF",
                "application/pdf",
                AttachmentExtractionStatus.SUCCEEDED,
                "附件完整正文",
                "attachment-hash",
                6,
                null
        );
        PolicyCrawlResult crawl = new PolicyCrawlResult(
                "政策标题",
                "发布单位",
                LocalDate.of(2026, 8, 1),
                policy.getSourceUrl(),
                "通知正文",
                LocalDateTime.now(),
                "example.gov.cn",
                "GOVERNMENT",
                "NATIONAL_AUTHORITY",
                "PAGE_METADATA_OR_TEXT",
                "2026-08-01",
                "MEDIUM",
                "new-hash",
                "证据",
                "通知正文\n\n【附件：行动方案.pdf】\n附件完整正文",
                ContentCompleteness.COMPLETE,
                "附件已完整解析",
                List.of(attachment)
        );
        when(policyRepository.findById(7L)).thenReturn(Optional.of(policy));
        when(crawlerService.crawl(policy.getSourceUrl())).thenReturn(crawl);
        when(policyRepository.save(policy)).thenReturn(policy);

        PolicyDocument result = service.refreshAccepted(7L);

        assertThat(result.getCleanedContent()).contains("附件完整正文");
        assertThat(result.getContentCompleteness()).isEqualTo(ContentCompleteness.COMPLETE);
        assertThat(result.getAttachmentCount()).isEqualTo(1);
        assertThat(result.getExtractedAttachmentCount()).isEqualTo(1);
        assertThat(result.getAnalysisStatus()).isEqualTo(PolicyAnalysisStatus.READY);
        verify(persistenceService).upsert(7L, List.of(attachment));
    }

    @Test
    void shouldRejectRefreshForPolicyNotAcceptedByHuman() {
        PolicyDocument policy = new PolicyDocument();
        policy.setId(7L);
        policy.setReviewStatus(PolicyReviewStatus.PENDING);
        when(policyRepository.findById(7L)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.refreshAccepted(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已采纳");

        verifyNoInteractions(crawlerService, persistenceService);
    }
}
