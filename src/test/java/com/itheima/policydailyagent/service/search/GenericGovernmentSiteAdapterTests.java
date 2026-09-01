package com.itheima.policydailyagent.service.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GenericGovernmentSiteAdapterTests {

    private final GenericGovernmentSiteAdapter adapter = new GenericGovernmentSiteAdapter();

    @ParameterizedTest
    @MethodSource("governmentFixtures")
    void shouldDiscoverKeywordMatchedArticleLinks(
            String fixture,
            String sourceUrl,
            String expectedDomain
    ) throws Exception {
        String resource = "/fixtures/search/" + fixture;
        String html;
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        var links = adapter.discoverFromHtml(
                sourceUrl,
                html,
                List.of("人工智能"),
                10
        );

        assertThat(links).hasSize(1);
        assertThat(links.get(0).url())
                .contains(expectedDomain)
                .doesNotContain("#");
        assertThat(links.get(0).title()).contains("人工智能");
    }

    static Stream<Arguments> governmentFixtures() {
        return Stream.of(
                Arguments.of("gov.html", "https://www.gov.cn/zhengce/zuixin/", "www.gov.cn"),
                Arguments.of("miit.html", "https://www.miit.gov.cn/zwgk/zcwj/", "www.miit.gov.cn"),
                Arguments.of("ndrc.html", "https://www.ndrc.gov.cn/xxgk/zcfb/", "www.ndrc.gov.cn"),
                Arguments.of("shandong.html", "https://www.shandong.gov.cn/col/col107851/", "www.shandong.gov.cn"),
                Arguments.of("shandong-gxt.html", "https://gxt.shandong.gov.cn/col/col103863/", "gxt.shandong.gov.cn")
        );
    }
}
