package com.eformworks.signstage.backend.feature.order.service;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 감액·이의·고액 지급 통제 — signstage-docs business/platform-order-partner-subcontract-review.md D13·D19
 * (2026-11-12 권장안으로 결정).
 *
 * <ul>
 *   <li>감액이 발주 금액의 {@code signstage.platform-order.large-deduction-rate}(기본 0.3)를 넘으면
 *       PLATFORM_SUPER만 승인할 수 있다.</li>
 *   <li>파트너는 감액된 검수에 승인일부터 {@code signstage.platform-order.dispute-days}(기본 7일) 안에
 *       이의를 제기할 수 있다. 그 기간과 이의 제기 중에는 해당 검수를 정산하지 않는다.</li>
 *   <li>지급액(공급가)이 {@code signstage.platform-order.payout-approval-threshold}(기본 1,000만) 이상인
 *       지급 건은 PLATFORM_SUPER가 승인해야 지급 완료로 기록할 수 있다. 정산을 만든 사람은 승인할 수 없다.</li>
 * </ul>
 */
@Component
public class PartnerPaymentControlPolicy {

    private final BigDecimal largeDeductionRate;
    private final int disputeDays;
    private final BigDecimal payoutApprovalThreshold;

    public PartnerPaymentControlPolicy(
            @Value("${signstage.platform-order.large-deduction-rate:0.3}") BigDecimal largeDeductionRate,
            @Value("${signstage.platform-order.dispute-days:7}") int disputeDays,
            @Value("${signstage.platform-order.payout-approval-threshold:10000000}") BigDecimal payoutApprovalThreshold
    ) {
        this.largeDeductionRate = largeDeductionRate;
        this.disputeDays = disputeDays;
        this.payoutApprovalThreshold = payoutApprovalThreshold;
    }

    public boolean isLargeDeduction(BigDecimal orderAmount, BigDecimal deductionAmount) {
        return deductionAmount.compareTo(orderAmount.multiply(largeDeductionRate)) > 0;
    }

    public int disputeDays() {
        return disputeDays;
    }

    public boolean requiresPayoutApproval(BigDecimal payoutAmount) {
        return payoutAmount.compareTo(payoutApprovalThreshold) >= 0;
    }
}
