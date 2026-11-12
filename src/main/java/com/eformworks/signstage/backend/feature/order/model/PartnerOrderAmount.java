package com.eformworks.signstage.backend.feature.order.model;

import java.math.BigDecimal;

/**
 * 발주 금액 계산 결과 — signstage-docs business/platform-order-partner-subcontract-review.md 6.2절.
 * {@code totalAmount = baseFee + (tabletCount + spareTabletCount) × tabletUnitRate + wifiCount × wifiUnitRate
 * + (onsiteStaffPersonDays + rehearsalStaffPersonDays) × onsiteStaffDayRate + regionSurcharge
 * + lodgingPersonNights × lodgingNightRate + adjustmentAmount}. {@code partnerRateCardId}·{@code regionSurchargeId}는
 * 계산에 쓴 가격표 행이다(가격표 사용 여부 판단).
 */
public record PartnerOrderAmount(
        BigDecimal baseFee,
        int tabletCount,
        BigDecimal tabletUnitRate,
        int onsiteStaffPersonDays,
        BigDecimal onsiteStaffDayRate,
        BigDecimal regionSurcharge,
        BigDecimal adjustmentAmount,
        BigDecimal totalAmount,
        int spareTabletCount,
        int rehearsalStaffPersonDays,
        int lodgingPersonNights,
        BigDecimal lodgingNightRate,
        Long partnerRateCardId,
        Long regionSurchargeId,
        int wifiCount,
        BigDecimal wifiUnitRate
) {
}
