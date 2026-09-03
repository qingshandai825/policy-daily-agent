package com.itheima.policydailyagent.service.search;

/**
 * 信源失败检查点写入失败异常：表示「必要的信源失败检查点」因数据库不可用等原因未能可靠
 * 持久化。该异常绝不允许被当作普通业务异常吞掉后继续宣称检查点已保存——抛出后由多轮
 * 流程把轮次标记为可识别的中断状态（stop_reason=CHECKPOINT_FAILED），数据库恢复后即可
 * 重新运行以重试该检查点的持久化。
 *
 * <p>与 {@link LeaseLostException} 不同：后者表示执行权已丢失（不得再写入任何东西），
 * 本异常表示仍持有执行权、但关键检查点未能落库，应进入可识别的失败/中断状态。
 */
public class SourceFailureCheckpointException extends RuntimeException {

    public SourceFailureCheckpointException(Long taskId, String sourceId, Throwable cause) {
        super("信源失败检查点写入失败，taskId=" + taskId + ", sourceId=" + sourceId, cause);
    }
}
