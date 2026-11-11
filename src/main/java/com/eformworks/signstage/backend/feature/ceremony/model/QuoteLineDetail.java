package com.eformworks.signstage.backend.feature.ceremony.model;

import com.eformworks.signstage.backend.feature.billing.entity.UnitProductCategory;
import java.math.BigDecimal;

/** {@code billing_quote_lines} 한 줄과 1:1로 대응하는 완전한 계산 결과. */
public record QuoteLineDetail(
        String lineType,
        Long itemId,
        String itemName,
        UnitProductCategory category,
        boolean platformUsageFee,
        int quantity,
        BigDecimal unitListAmount,
        BigDecimal listAmount,
        BigDecimal itemDiscountAmount,
        BigDecimal ceremonyDiscountAmount,
        BigDecimal netAmount,
        String taxCode,
        String taxCategory,
        BigDecimal taxRatePercent,
        String priceInclusion,
        BigDecimal taxAmount,
        BigDecimal grossAmount
) {
}
