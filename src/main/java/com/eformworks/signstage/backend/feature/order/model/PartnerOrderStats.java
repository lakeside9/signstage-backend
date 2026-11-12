package com.eformworks.signstage.backend.feature.order.model;

import java.time.LocalDateTime;

/**
 * 파트너별 발주 응답 집계 — 파트너 평가(signstage-docs business/platform-order-partner-subcontract-review.md
 * 11장 8단계). 응답 기한이 지난 ISSUED는 만료로 센다.
 */
public record PartnerOrderStats(
        Long organizationId,
        long issuedCount,
        long acceptedCount,
        long rejectedCount,
        long expiredCount,
        LocalDateTime lastIssuedAt
) {
}
