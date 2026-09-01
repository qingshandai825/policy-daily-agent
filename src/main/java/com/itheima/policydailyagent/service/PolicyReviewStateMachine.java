package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import org.springframework.stereotype.Component;

@Component
public class PolicyReviewStateMachine {

    public void validateTransition(PolicyReviewStatus current, PolicyReviewStatus target) {
        if (current == null) {
            current = PolicyReviewStatus.PENDING;
        }
        if (target == null) {
            throw new IllegalArgumentException("目标审核状态不能为空");
        }
        if (current == target) {
            throw new IllegalArgumentException("政策已经处于 " + target + " 状态");
        }
    }
}
