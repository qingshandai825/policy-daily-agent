package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.AttachmentExtractionStatus;
import com.itheima.policydailyagent.domain.policy.ContentCompleteness;
import com.itheima.policydailyagent.dto.PolicyAttachmentCrawlResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyAttachmentCrawlerServiceTests {

    private final PolicyAttachmentCrawlerService service = new PolicyAttachmentCrawlerService();

    @Test
    void shouldDiscoverSupportedPolicyAttachmentsAndIgnoreSpreadsheets() {
        var document = Jsoup.parse(
                """
                <html><body>
                  <a href="P020260817001.pdf">下载</a>
                  <a href="/files/P020260817001.ofd">《行动方案》.ofd</a>
                  <a href="/files/statistics.xlsx">附件统计表.xlsx</a>
                </body></html>
                """,
                "https://example.gov.cn/policy/notice.html"
        );

        var links = service.discover(document, "https://example.gov.cn/policy/notice.html");

        assertThat(links).hasSize(2);
        assertThat(links).extracting(PolicyAttachmentCrawlerService.AttachmentLink::fileType)
                .containsExactly("PDF", "OFD");
        assertThat(links.get(0).fileName()).isEqualTo("P020260817001.pdf");
        assertThat(links.get(1).sourceUrl())
                .isEqualTo("https://example.gov.cn/files/P020260817001.ofd");
    }

    @Test
    void shouldTreatPdfAndOfdAlternativesAsOneCoveredAttachmentGroup() {
        PolicyAttachmentCrawlResult pdf = attachment(
                "《行动方案》.pdf",
                "PDF",
                AttachmentExtractionStatus.SUCCEEDED,
                "行动方案完整正文，包含总体要求、重点任务和保障措施。"
        );
        PolicyAttachmentCrawlResult ofd = attachment(
                "《行动方案》.ofd",
                "OFD",
                AttachmentExtractionStatus.UNSUPPORTED,
                null
        );

        var result = service.assess("通知正文", List.of(pdf, ofd));

        assertThat(result.contentCompleteness()).isEqualTo(ContentCompleteness.COMPLETE);
        assertThat(result.cleanedContent())
                .contains("通知正文", "【附件：《行动方案》.pdf】", "重点任务");
        assertThat(result.contentQualityReason()).contains("1 组");
    }

    @Test
    void shouldMarkPageOnlyWhenNoAttachmentCanBeParsed() {
        PolicyAttachmentCrawlResult ofd = attachment(
                "行动方案.ofd",
                "OFD",
                AttachmentExtractionStatus.UNSUPPORTED,
                null
        );

        var result = service.assess("通知正文", List.of(ofd));

        assertThat(result.contentCompleteness()).isEqualTo(ContentCompleteness.PAGE_ONLY);
        assertThat(result.cleanedContent()).isEqualTo("通知正文");
    }

    @Test
    void shouldExtractTextFromPdf() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                stream.newLineAtOffset(40, 700);
                stream.showText(
                        "Policy action plan contains objectives, key tasks, support measures, "
                                + "implementation requirements and measurable targets for industry."
                );
                stream.endText();
            }
            document.save(output);
            bytes = output.toByteArray();
        }

        assertThat(service.parseAttachmentBytes(bytes, "PDF"))
                .contains("Policy action plan", "measurable targets");
    }

    @Test
    void shouldExtractTextFromDocx() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(
                    "政策行动方案包括总体要求、重点任务、支持措施和组织实施要求。"
            );
            document.write(output);
            bytes = output.toByteArray();
        }

        assertThat(service.parseAttachmentBytes(bytes, "DOCX"))
                .contains("总体要求", "组织实施");
    }

    private PolicyAttachmentCrawlResult attachment(
            String fileName,
            String fileType,
            AttachmentExtractionStatus status,
            String content
    ) {
        return new PolicyAttachmentCrawlResult(
                fileName,
                "https://example.gov.cn/files/" + fileName,
                fileType,
                "application/octet-stream",
                status,
                content,
                content == null ? null : "hash",
                content == null ? 0 : content.length(),
                status == AttachmentExtractionStatus.SUCCEEDED ? null : "暂不支持"
        );
    }
}
