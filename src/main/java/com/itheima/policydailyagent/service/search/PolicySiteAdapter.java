package com.itheima.policydailyagent.service.search;

import java.util.List;

public interface PolicySiteAdapter {

    boolean supports(String sourceUrl);

    List<DiscoveredPolicyLink> discover(
            String sourceUrl,
            List<String> keywords,
            int maxLinks,
            boolean filterByKeyword
    );

    record DiscoveredPolicyLink(
            String title,
            String url,
            String snippet
    ) {
    }
}
