package com.eformworks.signstage.backend.core.money;

import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** 통화별 반올림을 적용하는 공용 금액/할인/세금 계산기. */
@Component
public class MoneyCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext INTERMEDIATE_CONTEXT = new MathContext(19, RoundingMode.HALF_UP);

    public BigDecimal normalize(BigDecimal amount, CurrencyPolicy policy) {
        requireAmount(amount);
        return amount.setScale(policy.fractionDigits(), policy.roundingMode());
    }

    public BigDecimal applyDiscount(
            BigDecimal amount,
            DiscountType discountType,
            BigDecimal discountValue,
            CurrencyPolicy policy
    ) {
        requireAmount(amount);
        requireAmount(discountValue);
        if (discountType == null) {
            throw new IllegalArgumentException("discountType is required");
        }
        BigDecimal discount = discountType == DiscountType.PERCENT
                ? amount.multiply(discountValue, INTERMEDIATE_CONTEXT).divide(ONE_HUNDRED, INTERMEDIATE_CONTEXT)
                : discountValue;
        return normalize(amount.subtract(discount).max(BigDecimal.ZERO), policy);
    }

    /** 세금 별도(EXCLUSIVE) 금액의 라인 세액. */
    public BigDecimal calculateExclusiveTax(BigDecimal netAmount, BigDecimal ratePercent, CurrencyPolicy policy) {
        requireAmount(netAmount);
        requireAmount(ratePercent);
        return normalize(netAmount.multiply(ratePercent, INTERMEDIATE_CONTEXT).divide(ONE_HUNDRED, INTERMEDIATE_CONTEXT), policy);
    }

    private static void requireAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
    }
}
