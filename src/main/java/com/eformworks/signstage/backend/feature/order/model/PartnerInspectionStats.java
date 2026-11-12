package com.eformworks.signstage.backend.feature.order.model;

import java.math.BigDecimal;

/** 파트너별 승인된 검수 집계 — 파트너 평가. 평점은 입력한 건만 평균한다. */
public record PartnerInspectionStats(
        Long organizationId,
        long approvedCount,
        long deemedCount,
        long defectCount,
        long delayCount,
        long deductionCount,
        BigDecimal deductionTotal,
        Double averageRating,
        long ratedCount
) {
}
