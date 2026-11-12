package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 최소 마진 검사 — signstage-docs business/platform-order-partner-subcontract-review.md 6.3절, D06.
 * {@code 고객 공급가 − 발주 금액 ≥ 고객 공급가 × 최소 마진율}이어야 한다. 시스템 원가는 아직 넣지
 * 않는다(D07 "내부 원가 산정" 미정). 기준(D06 최소 마진 기준)이 정해지기 전이라 기본값은 0(적자 수주만
 * 막음)이고 {@code signstage.platform-order.minimum-margin-rate}로 바꾼다.
 */
@Component
public class PlatformOrderMarginPolicy {

    private final BigDecimal minimumMarginRate;

    public PlatformOrderMarginPolicy(
            @Value("${signstage.platform-order.minimum-margin-rate:0}") BigDecimal minimumMarginRate
    ) {
        this.minimumMarginRate = minimumMarginRate;
    }

    public boolean isMarginSufficient(BigDecimal customerSupplyAmount, BigDecimal partnerAmount) {
        BigDecimal margin = customerSupplyAmount.subtract(partnerAmount);
        BigDecimal minimum = customerSupplyAmount.multiply(minimumMarginRate);
        return margin.compareTo(minimum) >= 0;
    }

    public void checkMargin(BigDecimal customerSupplyAmount, BigDecimal partnerAmount) {
        if (!isMarginSufficient(customerSupplyAmount, partnerAmount)) {
            throw new ApplicationException(OrderErrorCode.MARGIN_BELOW_MINIMUM);
        }
    }
}
