package com.eformworks.signstage.backend.feature.ceremony.model;

import java.math.BigDecimal;
import java.util.List;

/** {@code CeremonyQuoteService#buildQuoteCalculation}의 계산 결과 헤더 + 라인 상세 — 확정 견적 스냅샷의 원본. */
public record QuoteCalculation(
        BigDecimal planAppliedPrice,
        BigDecimal unitProductPurchasesTotal,
        BigDecimal subtotal,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal grossAmount,
        List<QuoteLineDetail> lines
) {
}
