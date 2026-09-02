package com.itheima.policydailyagent.service.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.itheima.policydailyagent.domain.search.SearchTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTaskMemoryAssemblerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final SearchTaskMemoryAssembler assembler = new SearchTaskMemoryAssembler(OBJECT_MAPPER);

    @Test
    void contextJsonIsStableAndRoundTrips() {
        SearchTask task = task();
        SearchTaskMemoryContext context = assembler.contextOf(
                task,
                List.of("gov", "miit"),
                List.of(10L, 11L),
                SearchTaskMemoryContext.Counts.of(3, 2, 0, 1, 1),
                List.of(new SearchTaskMemoryContext.SourceFailure("miit", "工信部", "连接超时"))
        );

        String json = assembler.toJson(context);

        assertThat(json)
                .contains("\"reportMonth\":\"2026-08\"")
                .contains("\"keywords\":[\"人工智能\",\"大模型\"]")
                .contains("\"selectedSources\":[\"gov\",\"miit\"]")
                .contains("\"candidatePolicyIds\":[10,11]")
                .contains("\"found\":3");

        SearchTaskMemoryContext parsed = assembler.parseContext(json);
        assertThat(parsed).isEqualTo(context);
    }

    @Test
    void contextJsonHandlesNullOrBlank() {
        assertThat(assembler.parseContext(null)).isNull();
        assertThat(assembler.parseContext("   ")).isNull();
    }

    @Test
    void contextOfUsesEmptyListsWhenTaskHasNoMetadata() {
        SearchTask task = new SearchTask();
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));

        SearchTaskMemoryContext context = assembler.contextOf(
                task, null, null, null, null);

        assertThat(context.keywords()).isEmpty();
        assertThat(context.selectedSources()).isEmpty();
        assertThat(context.executedSources()).isEmpty();
        assertThat(context.candidatePolicyIds()).isEmpty();
        assertThat(context.sourceFailures()).isEmpty();
        assertThat(context.counts()).isEqualTo(SearchTaskMemoryContext.Counts.zero());
    }

    private SearchTask task() {
        SearchTask task = new SearchTask();
        task.setId(7L);
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能,大模型");
        task.setSourceIds("gov,miit");
        return task;
    }
}
