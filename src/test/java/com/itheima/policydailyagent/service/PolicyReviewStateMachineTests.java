package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.policy.PolicyReviewStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyReviewStateMachineTests {

    private final PolicyReviewStateMachine stateMachine = new PolicyReviewStateMachine();

    @Test
    void shouldAllowHumanToChangeDecision() {
        assertThatCode(() -> stateMachine.validateTransition(
                PolicyReviewStatus.PENDING,
                PolicyReviewStatus.ACCEPTED
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDuplicateDecision() {
        assertThatThrownBy(() -> stateMachine.validateTransition(
                PolicyReviewStatus.ACCEPTED,
                PolicyReviewStatus.ACCEPTED
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已经处于");
    }
}
