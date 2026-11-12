package com.eformworks.signstage.backend.feature.order.service;

import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 검수·지급 기한 — signstage-docs business/platform-order-partner-subcontract-review.md D11(2026-11-12 결정).
 *
 * <ul>
 *   <li>검수 기한은 행사 완료일 + {@code signstage.platform-order.inspection-days}(기본 7일)이다. 기한까지
 *       검수하지 않으면 승인으로 본다.</li>
 *   <li>지급은 검수 승인월 단위로 합산하고, 기한은 다음 달 말일이되 가장 이른 승인일부터
 *       {@code signstage.platform-order.payment-max-days}(기본 60일, 하도급법 상한)를 넘지 않는다.</li>
 * </ul>
 */
@Component
public class PartnerSettlementPolicy {

    private final int inspectionDays;
    private final int paymentMaxDays;

    public PartnerSettlementPolicy(
            @Value("${signstage.platform-order.inspection-days:7}") int inspectionDays,
            @Value("${signstage.platform-order.payment-max-days:60}") int paymentMaxDays
    ) {
        this.inspectionDays = inspectionDays;
        this.paymentMaxDays = paymentMaxDays;
    }

    public LocalDate inspectionDueDate(LocalDate completedDate) {
        return completedDate.plusDays(inspectionDays);
    }

    public LocalDate paymentDueDate(YearMonth settlementMonth, LocalDate earliestApprovedDate) {
        LocalDate nextMonthEnd = settlementMonth.plusMonths(1).atEndOfMonth();
        LocalDate legalLimit = earliestApprovedDate.plusDays(paymentMaxDays);
        return nextMonthEnd.isBefore(legalLimit) ? nextMonthEnd : legalLimit;
    }
}
