package com.eformworks.signstage.backend.feature.order.service;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 고객 선금 비율 — signstage-docs business/platform-order-partner-subcontract-review.md D12(2026-11-12 결정:
 * 선금+잔금). 수주 금액 × {@code signstage.platform-order.advance-payment-rate}(기본 0.3)를 수주 확정 후
 * 선금으로 청구하고, 나머지를 행사 완료 후 잔금으로 청구한다. 0이면 선금 없이 잔금만 청구한다.
 */
@Component
public class CustomerBillingPolicy {

    private final BigDecimal advancePaymentRate;

    public CustomerBillingPolicy(
            @Value("${signstage.platform-order.advance-payment-rate:0.3}") BigDecimal advancePaymentRate
    ) {
        if (advancePaymentRate.signum() < 0 || advancePaymentRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("advance-payment-rate must be in [0, 1)");
        }
        this.advancePaymentRate = advancePaymentRate;
    }

    public BigDecimal advancePaymentRate() {
        return advancePaymentRate;
    }

    public boolean isAdvanceEnabled() {
        return advancePaymentRate.signum() > 0;
    }
}
