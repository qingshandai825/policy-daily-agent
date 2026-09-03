package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskRound;
import com.itheima.policydailyagent.domain.search.SearchTaskRoundStatus;
import com.itheima.policydailyagent.repository.SearchTaskRepository;
import com.itheima.policydailyagent.repository.SearchTaskRoundRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用真实 JPA 持久化（H2 create-drop）验证轮次记录的写入、顺序读取，
 * 以及 (search_task_id, round_no) 唯一约束。
 */
@SpringBootTest
@ActiveProfiles("test")
class SearchTaskRoundPersistenceTests {

    @Autowired
    private SearchTaskRepository searchTaskRepository;

    @Autowired
    private SearchTaskRoundRepository roundRepository;

    @Test
    void roundPersistsAndLoadsInRoundOrder() {
        SearchTask task = searchTaskRepository.saveAndFlush(task("轮次持久化测试"));
        roundRepository.saveAndFlush(round(task.getId(), 1, SearchTaskRoundStatus.COMPLETED));
        roundRepository.saveAndFlush(round(task.getId(), 2, SearchTaskRoundStatus.PLANNED));

        List<SearchTaskRound> rounds = roundRepository.findBySearchTaskIdOrderByRoundNoAsc(task.getId());

        assertThat(rounds).extracting(SearchTaskRound::getRoundNo).containsExactly(1, 2);
        assertThat(rounds.get(0).getStatus()).isEqualTo(SearchTaskRoundStatus.COMPLETED);
        assertThat(rounds.get(0).getKeywordsJson()).contains("人工智能");
        assertThat(rounds.get(0).getSearchTaskId()).isEqualTo(task.getId());
    }

    @Test
    void duplicateRoundNoIsRejected() {
        SearchTask task = searchTaskRepository.saveAndFlush(task("唯一约束测试"));
        roundRepository.saveAndFlush(round(task.getId(), 1, SearchTaskRoundStatus.COMPLETED));

        assertThatThrownBy(() -> roundRepository.saveAndFlush(
                round(task.getId(), 1, SearchTaskRoundStatus.PLANNED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private SearchTaskRound round(Long taskId, int roundNo, SearchTaskRoundStatus status) {
        SearchTaskRound round = new SearchTaskRound();
        round.setSearchTaskId(taskId);
        round.setRoundNo(roundNo);
        round.setStatus(status);
        round.setPlanJson("{\"keywords\":[\"人工智能\"]}");
        round.setKeywordsJson("[\"人工智能\"]");
        round.setTargetSourcesJson("[\"gov\"]");
        return round;
    }

    private SearchTask task(String taskName) {
        SearchTask task = new SearchTask();
        task.setTaskName(taskName);
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能");
        task.setSourceIds("gov");
        task.markRunning();
        return task;
    }
}
