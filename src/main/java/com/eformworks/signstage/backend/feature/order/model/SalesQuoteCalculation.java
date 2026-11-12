package com.eformworks.signstage.backend.feature.order.model;

import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLineType;
import java.math.BigDecimal;
import java.util.List;

/**
 * 고객 견적 계산 결과 — {@code OrderPricingService#calculateSalesQuote}. 줄은 저장 순서대로다.
 *
 * @param supplyAmount 줄 금액 합계(할인 포함, 세금 별도)
 * @param tabletCount 플랜 포함 + 옵션 태블릿 대수(발주 금액 계산 입력)
 * @param onsiteStaffPersonDays 플랜 포함 + 옵션 현장 인력 인·일
 * @param extras 예비 태블릿·리허설 별도일 인력·숙박(D23·D24·D26)
 * @param regionSurchargeId 계산에 쓴 권역 할증 행(가격표 사용 여부 판단)
 */
public record SalesQuoteCalculation(
        List<Line> lines,
        BigDecimal supplyAmount,
        BigDecimal vatAmount,
        int tabletCount,
        int onsiteStaffPersonDays,
        ScopeExtras extras,
        Long regionSurchargeId
) {

    /** @param customerPriceId 단가를 가져온 고객 가격 행. 할인·할증·숙박 줄은 null이다. */
    public record Line(
            SalesOrderLineType lineType,
            UnitProduct unitProduct,
            String name,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            String taxCode,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            Long customerPriceId
    ) {
    }
}
