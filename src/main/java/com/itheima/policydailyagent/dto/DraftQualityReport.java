package com.itheima.policydailyagent.dto;

import java.util.List;

/** 程序检查与修订轨迹，不保存模型隐式推理或重复的全文。 */
public record DraftQualityReport(boolean passed, int revisionCount, List<Attempt> attempts) {
    public record Attempt(int attemptNo, List<String> issues) {}
}
