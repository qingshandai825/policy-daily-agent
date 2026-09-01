package com.itheima.policydailyagent.template;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyReportTemplateContractTests {

    @Test
    void shouldUseNoAttachmentThreeSectionTemplateWithRequiredSlots() throws Exception {
        try (InputStream input = new ClassPathResource(
                "templates/monthly_report_template.docx"
        ).getInputStream(); XWPFDocument document = new XWPFDocument(input)) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);

            long paragraphSectionBreaks = document.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getCTP().isSetPPr())
                    .filter(paragraph -> paragraph.getCTP().getPPr().isSetSectPr())
                    .count();
            long sectionCount = paragraphSectionBreaks
                    + (document.getDocument().getBody().isSetSectPr() ? 1 : 0);

            assertThat(document.getTables()).isEmpty();
            assertThat(sectionCount).isEqualTo(3);
            assertThat(text).doesNotContain("附件1", "附件进度统计表");

            Set<String> required = Set.of(
                    "{{REPORT_YEAR}}",
                    "{{ISSUE_NO}}",
                    "{{TOTAL_ISSUE_NO}}",
                    "{{REPORT_MONTH}}",
                    "{{SECTION_NATIONAL}}",
                    "{{SECTION_PROVINCIAL}}",
                    "{{SECTION_PIONEER_OVERALL}}",
                    "{{SECTION_PIONEER_INDICATORS}}",
                    "{{SECTION_PIONEER_SUPPORT}}",
                    "{{SECTION_PIONEER_EXPERIENCE}}",
                    "{{SECTION_CASE}}",
                    "{{SECTION_TREND}}"
            );
            assertThat(required).allMatch(text::contains);
        }
    }
}
