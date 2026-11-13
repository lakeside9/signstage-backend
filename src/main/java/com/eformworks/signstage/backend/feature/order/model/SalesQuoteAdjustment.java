package com.eformworks.signstage.backend.feature.order.model;

import com.eformworks.signstage.backend.core.money.DiscountType;
import java.math.BigDecimal;
import java.util.Set;

/**
 * 견적 금액 조정 — 영업 할인(정액 또는 퍼센트)과 공급가(부가세 별도)의 단수 절사 단위.
 *
 * @param discountAmount 정액 할인. {@code discountRatePercent}가 있으면 쓰지 않는다.
 * @param discountRatePercent 퍼센트 할인율(0~100). 할인 전 공급가 소계에 곱한다.
 * @param truncationUnit 할인 뒤 공급가를 이 단위 미만에서 버리고, 부가세는 절사한 공급가로 계산한다(10·100·1,000·10,000). null이면 절사하지 않는다.
 */
public record SalesQuoteAdjustment(BigDecimal discountAmount, BigDecimal discountRatePercent, Integer truncationUnit) {

    public static final SalesQuoteAdjustment NONE = new SalesQuoteAdjustment(null, null, null);
    public static final Set<Integer> TRUNCATION_UNITS = Set.of(10, 100, 1_000, 10_000);

    public static SalesQuoteAdjustment fixed(BigDecimal discountAmount) {
        return new SalesQuoteAdjustment(discountAmount, null, null);
    }

    /** 요청의 할인 방식에 맞는 값만 남긴다 — 방식을 생략하면 정액이다. */
    public static SalesQuoteAdjustment of(
            DiscountType discountType, BigDecimal discountAmount, BigDecimal discountRatePercent, Integer truncationUnit
    ) {
        return discountType == DiscountType.PERCENT
                ? new SalesQuoteAdjustment(null, discountRatePercent, truncationUnit)
                : new SalesQuoteAdjustment(discountAmount, null, truncationUnit);
    }
}
