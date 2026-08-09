package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.agent.dto.MonthlyPioneerContent;
import com.itheima.policydailyagent.agent.dto.MonthlyReportContent;
import com.itheima.policydailyagent.agent.dto.MonthlyReportItem;
import com.itheima.policydailyagent.agent.dto.MonthlyReportSection;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyReportTemplateServiceTests {

    private final MonthlyReportTemplateService service = new MonthlyReportTemplateService();

    @Test
    void shouldFillOriginalTemplateAndPreserveItsStructuralParts() throws Exception {
        byte[] report = service.fill(content());
        Path sample = Path.of("target", "qa", "monthly-template", "monthly-report-sample.docx");
        Files.createDirectories(sample.getParent());
        Files.write(sample, report);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(report))) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .collect(Collectors.joining("\n"));

            assertThat(text)
                    .contains("（2026年第8期，总第12期）")
                    .contains("2026年8月")
                    .contains("国家部署人工智能赋能制造业")
                    .contains("国家第二项政策部署")
                    .contains("省内推进供需对接")
                    .contains("先锋应用总体按计划推进")
                    .contains("企业案例取得明确成效")
                    .contains("工业智能体应用加快")
                    .contains("截止到2026年8月31日")
                    .contains("报：省有关领导")
                    .contains("送：各市工业和信息化局")
                    .contains("联系人及联系方式：张三 0531-12345678")
                    .doesNotContain("{{")
                    .doesNotContain("【栏目定位】")
                    .doesNotContain("【内容来源】")
                    .doesNotContain("【写作格式】")
                    .doesNotContain("【预期内容】")
                    .doesNotContain("【填报说明】")
                    .doesNotContain("XX年XX月XX日");

            assertThat(countSections(document)).isEqualTo(4);
            assertThat(document.getTables()).hasSize(1);
            assertThat(document.getTables().get(0).getNumberOfRows()).isEqualTo(4);
            assertThat(document.getTables().get(0).getCTTbl().getTblGrid().sizeOfGridColArray())
                    .isEqualTo(18);
            assertThat(document.getFooterList()).hasSize(3);
            assertThat(document.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getText().contains("第8期，总第12期"))
                    .findFirst()
                    .orElseThrow()
                    .getRuns().get(0)
                    .getCTR().getRPr().xmlText())
                    .contains("w:eastAsia=\"楷体_GB2312\"");
        }
    }

    private MonthlyReportContent content() {
        return new MonthlyReportContent(
                2026,
                8,
                12,
                "2026年8月",
                "2026年8月31日",
                "省有关领导",
                "各市工业和信息化局",
                "张三 0531-12345678",
                new MonthlyReportSection(
                        List.of("国家部署人工智能赋能制造业"),
                        List.of(
                                new MonthlyReportItem("国家重点事项", "国家层面完成政策部署。"),
                                new MonthlyReportItem("国家第二项政策部署", "第二项政策用于验证模板内连续插入段落。")
                        )
                ),
                section("省内推进供需对接", "省内工作推进", "山东省组织开展供需对接。"),
                new MonthlyPioneerContent(
                        List.of("先锋应用建设稳步推进"),
                        "先锋应用总体按计划推进。",
                        "重点指标完成情况以附件为准。",
                        "部分项目仍需加强资源对接。",
                        "已形成场景开放和供需协同做法。"
                ),
                section("企业案例取得明确成效", "典型企业案例", "企业在生产场景应用人工智能并取得成效。"),
                section("工业智能体应用加快", "工业智能体趋势", "多项材料显示工业智能体应用正在加快。")
        );
    }

    private MonthlyReportSection section(String keyPoint, String title, String body) {
        return new MonthlyReportSection(
                List.of(keyPoint),
                List.of(new MonthlyReportItem(title, body))
        );
    }

    private int countSections(XWPFDocument document) {
        int paragraphSections = (int) document.getParagraphs().stream()
                .filter(paragraph -> paragraph.getCTP().isSetPPr()
                        && paragraph.getCTP().getPPr().isSetSectPr())
                .count();
        return paragraphSections + (document.getDocument().getBody().isSetSectPr() ? 1 : 0);
    }
}
