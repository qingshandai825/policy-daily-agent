package com.itheima.policydailyagent.service.search;

/**
 * 执行权失效异常：执行器在续约或最终收尾时发现自己的执行权已被其他执行器接管
 * （租约到期后被抢占，或任务已被其他执行器完成/失败）。抛出后立即终止后续采集与
 * 状态推进，绝不覆盖新执行器的执行权与结果。
 *
 * <p>继承 {@link IllegalStateException} 使控制器将其映射为 400（竞争冲突），而非 500。
 */
public class LeaseLostException extends IllegalStateException {

    public LeaseLostException(Long taskId, String executorId) {
        super("执行权已失效或已被其他执行器接管，taskId=" + taskId + ", executorId=" + executorId);
    }
}
