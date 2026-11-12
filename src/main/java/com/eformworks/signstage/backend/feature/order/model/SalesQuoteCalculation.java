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
 * @param wifiCount 플랜 포함 + 옵션 와이파이 장비 대수
 * @param onsiteStaffPersonDays 플랜 포함 + 옵션 현장 인력 인·일
 * @param extras 예비 태블릿·리허설 별도일 인력·숙박(D23·D24·D26)
 * @param regionSurchargeId 계산에 쓴 권역 할증 행(가격표 사용 여부 판단)
 * @param customItemPartnerAmount 직접 입력 항목의 발주 금액 합계(수량 × 발주 단가)
 */
public record SalesQuoteCalculation(
        List<Line> lines,
        BigDecimal supplyAmount,
        BigDecimal vatAmount,
        int tabletCount,
        int onsiteStaffPersonDays,
        ScopeExtras extras,
        Long regionSurchargeId,
        int wifiCount,
        BigDecimal customItemPartnerAmount
) {

    /**
     * @param customerPriceId 단가를 가져온 고객 가격 행. 할인·할증·숙박·직접 입력 줄은 null이다.
     * @param partnerUnitPrice 직접 입력 줄의 발주 단가. 다른 줄은 null이다.
     */
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
            Long customerPriceId,
            BigDecimal partnerUnitPrice
    ) {

        public Line(
                SalesOrderLineType lineType, UnitProduct unitProduct, String name, int quantity, BigDecimal unitPrice,
                BigDecimal amount, String taxCode, BigDecimal taxRatePercent, BigDecimal taxAmount, Long customerPriceId
        ) {
            this(lineType, unitProduct, name, quantity, unitPrice, amount, taxCode, taxRatePercent, taxAmount, customerPriceId, null);
        }
    }
}
