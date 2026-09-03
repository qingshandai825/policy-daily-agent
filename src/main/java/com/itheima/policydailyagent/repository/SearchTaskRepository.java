package com.itheima.policydailyagent.repository;

import com.itheima.policydailyagent.domain.search.SearchTask;
import com.itheima.policydailyagent.domain.search.SearchTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SearchTaskRepository extends JpaRepository<SearchTask, Long> {

    List<SearchTask> findTop20ByOrderByCreatedAtDesc();

    /**
     * 悲观锁读取任务行（SELECT ... FOR UPDATE），用于「校验执行权 + 业务写入」的原子化：
     * 在持有该行锁的事务内读取并校验 executor_id，再写入轮次检查点，从而消除
     * 「先续约、再普通 save」之间的竞争窗口——校验与写入之间不可能再插入一次抢占。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SearchTask t where t.id = :id")
    Optional<SearchTask> findByIdForUpdate(@Param("id") Long id);

    /**
     * 原子抢占执行权：仅在任务仍为 RUNNING（可恢复）且无执行器或租约已过期时写入新执行器，
     * 避免两个并发 resume 同时推进，也避免「状态校验」与「抢占」之间的 TOCTOU——任务在抢占前
     * 被其他执行器完成时，因 status 不再是 RUNNING 而抢占失败。返回受影响行数，0 表示失败。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SearchTask t SET t.executorId = :executorId, t.leaseExpiresAt = :leaseUntil "
            + "WHERE t.id = :id AND t.status = com.itheima.policydailyagent.domain.search.SearchTaskStatus.RUNNING "
            + "AND (t.executorId IS NULL OR t.leaseExpiresAt IS NULL OR t.leaseExpiresAt < :now)")
    int claimExecutor(
            @Param("id") Long id,
            @Param("executorId") String executorId,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now
    );

    /**
     * 原子续约：仅当前持有执行权的执行器能续约。返回受影响行数，0 表示执行权已丢失。
     * 这是旧执行器隔离的关键 fencing——续约失败立即终止后续采集与写入。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SearchTask t SET t.leaseExpiresAt = :leaseUntil "
            + "WHERE t.id = :id AND t.executorId = :executorId")
    int renewLease(
            @Param("id") Long id,
            @Param("executorId") String executorId,
            @Param("leaseUntil") LocalDateTime leaseUntil
    );

    /**
     * 原子正常收尾：仅当前持有执行权的执行器能写入最终累计计数与终止原因，并释放执行权。
     * 返回受影响行数，0 表示执行权已丢失（不得覆盖新执行器结果）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SearchTask t SET t.status = :status, t.foundCount = :foundCount, "
            + "t.savedCount = :savedCount, t.duplicateCount = :duplicateCount, "
            + "t.filteredCount = :filteredCount, t.failedCount = :failedCount, "
            + "t.terminationReason = :terminationReason, t.completedAt = :completedAt, "
            + "t.executorId = null, t.leaseExpiresAt = null "
            + "WHERE t.id = :id AND t.executorId = :executorId")
    int finalizeAsCompleted(
            @Param("id") Long id,
            @Param("executorId") String executorId,
            @Param("status") SearchTaskStatus status,
            @Param("foundCount") int foundCount,
            @Param("savedCount") int savedCount,
            @Param("duplicateCount") int duplicateCount,
            @Param("filteredCount") int filteredCount,
            @Param("failedCount") int failedCount,
            @Param("terminationReason") String terminationReason,
            @Param("completedAt") LocalDateTime completedAt
    );

    /**
     * 原子失败收尾：仅当前持有执行权的执行器能写入失败态，并释放执行权。
     * 返回受影响行数，0 表示执行权已丢失（不得覆盖新执行器结果）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SearchTask t SET t.status = com.itheima.policydailyagent.domain.search.SearchTaskStatus.FAILED, "
            + "t.foundCount = :foundCount, t.savedCount = :savedCount, t.duplicateCount = :duplicateCount, "
            + "t.filteredCount = :filteredCount, t.failedCount = :failedCount, "
            + "t.failureMessage = :message, t.terminationReason = :terminationReason, "
            + "t.completedAt = :completedAt, t.executorId = null, t.leaseExpiresAt = null "
            + "WHERE t.id = :id AND t.executorId = :executorId")
    int finalizeAsFailed(
            @Param("id") Long id,
            @Param("executorId") String executorId,
            @Param("foundCount") int foundCount,
            @Param("savedCount") int savedCount,
            @Param("duplicateCount") int duplicateCount,
            @Param("filteredCount") int filteredCount,
            @Param("failedCount") int failedCount,
            @Param("message") String message,
            @Param("terminationReason") String terminationReason,
            @Param("completedAt") LocalDateTime completedAt
    );
}
