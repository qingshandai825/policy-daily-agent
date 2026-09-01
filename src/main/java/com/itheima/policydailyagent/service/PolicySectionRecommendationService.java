package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.report.ReportSection;
import com.itheima.policydailyagent.entity.PolicyDocument;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PolicySectionRecommendationService {

    public SectionRecommendation recommend(
            PolicyDocument policy,
            List<ReportSection> eligibleSections
    ) {
        if (eligibleSections == null || eligibleSections.isEmpty()) {
            throw new IllegalArgumentException("模板中没有可写入的月报栏目");
        }

        Set<String> codes = eligibleSections.stream()
                .map(ReportSection::getSectionCode)
                .collect(Collectors.toSet());
        String signals = String.join(" ",
                safe(policy.getTitle()),
                safe(policy.getSourceName()),
                safe(policy.getSourceDomain()),
                safe(policy.getCategory()),
                safe(policy.getPolicyType()),
                safe(policy.getKeywords())
        );

        if (containsAny(signals, "典型案例", "应用案例", "示范案例", "场景案例") && codes.contains("CASE")) {
            return new SectionRecommendation("CASE", "规则初判：材料主题以典型应用或示范案例为主");
        }
        if (containsAny(signals, "趋势", "白皮书", "研究报告", "产业洞察") && codes.contains("TREND")) {
            return new SectionRecommendation("TREND", "规则初判：材料主题以产业趋势或研究判断为主");
        }
        if (containsAny(signals, "山东", "山东省", "鲁政", "省工业和信息化厅", "省工信厅")
                && codes.contains("PROVINCIAL")) {
            return new SectionRecommendation("PROVINCIAL", "规则初判：发布主体或地域信号指向山东省内工作");
        }
        if (containsAny(signals, "国务院", "工业和信息化部", "国家发展改革委", "科技部", "国家数据局", "全国")
                && codes.contains("NATIONAL")) {
            return new SectionRecommendation("NATIONAL", "规则初判：发布主体属于国家层面");
        }
        if (codes.contains("NATIONAL")) {
            return new SectionRecommendation("NATIONAL", "规则初判：暂按国家重点事项候选，交由模型结合全文复核");
        }

        ReportSection first = eligibleSections.get(0);
        return new SectionRecommendation(first.getSectionCode(), "规则未形成明显倾向，交由模型结合全文复核");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record SectionRecommendation(
            String sectionCode,
            String reason
    ) {
    }
}
