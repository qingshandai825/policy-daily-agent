package com.itheima.policydailyagent.service.search;

import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskRound;
import com.itheima.policydailyagent.domain.search.SearchTaskRoundStatus;
import com.itheima.policydailyagent.domain.search.SearchTaskStatus;
import com.itheima.policydailyagent.repository.SearchTaskRepository;
import com.itheima.policydailyagent.repository.SearchTaskRoundRepository;
import com.itheima.policydailyagent.service.PolicyCrawlerService;
import com.itheima.policydailyagent.service.SearchTaskService;
import com.itheima.policydailyagent.service.memory.SearchTaskMemoryAssembler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 缺陷1（旧执行器对中间状态和检查点的写入隔离）回归测试。
 *
 * <p>确定性设计：核心原语 {@link SearchTaskService#withOwnership} 的「校验执行权 + 写入」
 * 原子性用单线程直接验证（B 先抢占、A 后写入 → 拒绝）；执行权规则用「租约已过期但未被接管」
 * 验证（ownership = executor_id 匹配，租约到期只允许抢占、不使仍持有 executor_id 的执行器
 * 失去执行权）；端到端接管用双线程 + 同步屏障 + 可控时钟验证，杜绝随机时序与长 sleep。
 */
@SpringBootTest(properties = {
        "policy.search.multi-round.enabled=true",
        "policy.search.multi-round.topics=人工智能,算力",
        "policy.search.multi-round.max-rounds=3",
        "policy.search.multi-round.no-growth-rounds=2",
        "policy.search.multi-round.coverage-threshold=1"
})
@ActiveProfiles("test")
@Import(SearchRoundOwnershipIsolationTests.MutableClockConfig.class)
class SearchRoundOwnershipIsolationTests {

    @Autowired
    private SearchTaskService searchTaskService;

    @Autowired
    private SearchTaskRepository searchTaskRepository;

    @Autowired
    private SearchTaskRoundRepository roundRepository;

    @Autowired
    private SearchRoundService roundService;

    @Autowired
    private SearchTaskMemoryAssembler assembler;

    @Autowired
    private MutableClock clock;

    @MockBean
    private PolicySiteAdapter siteAdapter;

    @MockBean
    private PolicyCrawlerService crawlerService;

    @Test
    void staleExecutorWithOwnershipRejected() {
        SearchTask task = searchTaskRepository.saveAndFlush(newTask("B"));

        SearchTaskRound round = new SearchTaskRound();
        round.setSearchTaskId(task.getId());
        round.setRoundNo(1);
        round.setStatus(SearchTaskRoundStatus.PLANNED);

        assertThatThrownBy(() -> searchTaskService.withOwnership(task.getId(), "A",
                () -> roundRepository.save(round)))
                .isInstanceOf(LeaseLostException.class);

        // 旧执行器 A 的检查点写入被原子拒绝：不产生任何轮次记录。
        assertThat(roundRepository.findBySearchTaskIdOrderByRoundNoAsc(task.getId())).isEmpty();
    }

    @Test
    void leaseExpiredButNotTakenOverStillOwned() {
        SearchTask task = newTask("A");
        task.setLeaseExpiresAt(LocalDateTime.now(clock).minusSeconds(1)); // 租约已过期
        task = searchTaskRepository.saveAndFlush(task);

        SearchTaskRound round = new SearchTaskRound();
        round.setSearchTaskId(task.getId());
        round.setRoundNo(1);
        round.setStatus(SearchTaskRoundStatus.PLANNED);

        // 执行权 = executor_id 匹配，租约到期只允许抢占、不使仍持有 executor_id 的执行器失效。
        searchTaskService.withOwnership(task.getId(), "A", () -> roundRepository.save(round));

        assertThat(roundRepository.findBySearchTaskIdOrderByRoundNoAsc(task.getId())).hasSize(1);
    }

    @Test
    void staleExecutorCannotFinalizeTask() {
        SearchTask task = searchTaskRepository.saveAndFlush(newTask("B"));
        Long taskId = task.getId();

        assertThatThrownBy(() -> searchTaskService.failWithCounts(
                taskId, "A", 1, 1, 0, 0, 1, "boom", "TASK_ERROR"))
                .isInstanceOf(LeaseLostException.class);

        SearchTask reloaded = searchTaskRepository.findById(taskId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SearchTaskStatus.RUNNING); // A 未覆盖任务状态
        assertThat(reloaded.getExecutorId()).isEqualTo("B");                  // B 的执行权未被修改
    }

    @Test
    void concurrentTakeoverOldExecutorAborts() throws Exception {
        Long taskId = seedInterruptedTask();
        when(siteAdapter.supports(anyString())).thenReturn(true);
        when(crawlerService.crawl(anyString()))
                .thenThrow(new RuntimeException("测试不应触发真实 HTTP 抓取"));

        CountDownLatch enteredDiscover = new CountDownLatch(1);
        CountDownLatch releaseDiscover = new CountDownLatch(1);
        when(siteAdapter.discover(anyString(), anyList(), anyInt(), anyBoolean())).thenAnswer(inv -> {
            enteredDiscover.countDown();
            releaseDiscover.await(10, TimeUnit.SECONDS);
            return List.of();
        });

        AtomicReference<Throwable> oldExecutorError = new AtomicReference<>();
        Thread a = new Thread(() -> {
            try {
                roundService.resume(taskId);
            } catch (Throwable t) {
                oldExecutorError.compareAndSet(null, t);
            }
        }, "old-executor-A");
        a.start();

        // A 已进入慢采集（持有 executor_id=A 且租约新鲜），此时推进时钟使租约过期，
        // 再由 B 原子抢占执行权，随后放行 A，验证 A 后续续约/写入被拒绝。
        assertThat(enteredDiscover.await(10, TimeUnit.SECONDS)).isTrue();
        clock.advance(1801);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean claimed = searchTaskService.claimExecutor(
                taskId, "B", now.plusSeconds(1800), now);
        assertThat(claimed).isTrue();
        releaseDiscover.countDown();

        a.join(30_000);

        assertThat(oldExecutorError.get()).isInstanceOf(LeaseLostException.class);
        assertThat(searchTaskRepository.findById(taskId).orElseThrow().getExecutorId())
                .isEqualTo("B");
    }

    private SearchTask newTask(String executorId) {
        SearchTask task = new SearchTask();
        task.setTaskName("隔离测试");
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能");
        task.setSourceIds("gov");
        task.setStatus(SearchTaskStatus.RUNNING);
        task.setExecutorId(executorId);
        return task;
    }

    private Long seedInterruptedTask() {
        SearchTask task = new SearchTask();
        task.setTaskName("隔离测试-中断");
        task.setReportMonth("2026-08");
        task.setTargetStartDate(LocalDate.of(2026, 8, 1));
        task.setTargetEndDate(LocalDate.of(2026, 8, 31));
        task.setKeywords("人工智能");
        task.setSourceIds("gov-latest");
        task.setStatus(SearchTaskStatus.RUNNING);
        task.setExecutorId("dead-executor");
        task.setLeaseExpiresAt(LocalDateTime.now(clock).minusSeconds(1));
        MultiRoundConfig config = new MultiRoundConfig(true, 3, 2, 1, 5, 2000, List.of("人工智能", "算力"));
        task.setRunParamsJson(assembler.toJson(SearchRunParams.of(5, false, true, config)));
        task = searchTaskRepository.saveAndFlush(task);
        Long taskId = task.getId();

        SearchTaskRound round1 = new SearchTaskRound();
        round1.setSearchTaskId(taskId);
        round1.setRoundNo(1);
        round1.setRetryNo(0);
        round1.setStatus(SearchTaskRoundStatus.PLANNED);
        round1.setKeywordsJson("[\"人工智能\"]");
        round1.setTargetSourcesJson("[\"gov-latest\"]");
        roundRepository.saveAndFlush(round1);

        return taskId;
    }

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-08-15T10:00:00Z"));
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant instant;
        private final ZoneId zone = ZoneOffset.UTC;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(long seconds) {
            this.instant = this.instant.plusSeconds(seconds);
        }
    }
}
