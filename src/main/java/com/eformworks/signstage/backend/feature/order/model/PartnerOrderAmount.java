package com.eformworks.signstage.backend.feature.order.model;

import java.math.BigDecimal;

/**
 * 발주 금액 계산 결과 — signstage-docs business/platform-order-partner-subcontract-review.md 6.2절.
 * {@code totalAmount = baseFee + (tabletCount + spareTabletCount) × tabletUnitRate
 * + (onsiteStaffPersonDays + rehearsalStaffPersonDays) × onsiteStaffDayRate + regionSurcharge
 * + lodgingPersonNights × lodgingNightRate + adjustmentAmount}.
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
        BigDecimal lodgingNightRate
) {
}
