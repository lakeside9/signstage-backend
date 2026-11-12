package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link PartnerSettlementPolicy} 단위 테스트 — 검수 기한과 지급 기한(D11). */
class PartnerSettlementPolicyTest {

    private final PartnerSettlementPolicy policy = new PartnerSettlementPolicy(7, 60);

    @Test
    @DisplayName("검수 기한은 행사 완료일 + 7일이다")
    void inspectionDueDate() {
        assertThat(policy.inspectionDueDate(LocalDate.of(2026, 11, 28))).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    @DisplayName("지급 기한은 승인월 다음 달 말일이다")
    void paymentDueDate_nextMonthEnd() {
        assertThat(policy.paymentDueDate(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 5)))
                .isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("다음 달 말일이 가장 이른 승인일 + 60일을 넘으면 60일째가 기한이다")
    void paymentDueDate_cappedByLegalLimit() {
        assertThat(policy.paymentDueDate(YearMonth.of(2026, 12), LocalDate.of(2026, 12, 1)))
                .isEqualTo(LocalDate.of(2027, 1, 30));
    }
}
