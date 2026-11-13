package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link CustomerCancellationPolicy} 단위 테스트 — 남은 일수 구간별 수수료·보상 비율. */
class CustomerCancellationPolicyTest {

    private final CustomerCancellationPolicy policy = new CustomerCancellationPolicy("30:0,14:30,7:50,0:100", "14:0,7:20,0:50");

    @Test
    @DisplayName("남은 일수가 구간 최소 일수 이상인 첫 구간을 쓴다")
    void picksTierByDaysBeforeEvent() {
        assertThat(policy.feeRatePercent(30L)).isEqualByComparingTo("0");
        assertThat(policy.feeRatePercent(29L)).isEqualByComparingTo("30");
        assertThat(policy.feeRatePercent(14L)).isEqualByComparingTo("30");
        assertThat(policy.feeRatePercent(7L)).isEqualByComparingTo("50");
        assertThat(policy.feeRatePercent(0L)).isEqualByComparingTo("100");
        assertThat(policy.compensationRatePercent(14L)).isEqualByComparingTo("0");
        assertThat(policy.compensationRatePercent(10L)).isEqualByComparingTo("20");
        assertThat(policy.compensationRatePercent(3L)).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("행사일이 지났으면 가장 가까운 구간, 행사일이 없으면 0%다")
    void handlesPastAndMissingEventDate() {
        assertThat(policy.feeRatePercent(-2L)).isEqualByComparingTo("100");
        assertThat(policy.feeRatePercent(null)).isEqualByComparingTo("0");
        assertThat(policy.compensationRatePercent(null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("비율이 0~100을 벗어나거나 형식이 틀리면 시작하지 않는다")
    void rejectsInvalidTiers() {
        assertThatThrownBy(() -> new CustomerCancellationPolicy("30:120", "0:0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerCancellationPolicy("30", "0:0")).isInstanceOf(IllegalArgumentException.class);
    }
}
