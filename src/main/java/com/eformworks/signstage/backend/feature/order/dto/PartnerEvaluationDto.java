package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.model.PartnerInspectionStats;
import com.eformworks.signstage.backend.feature.order.model.PartnerOrderStats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 파트너 평가(플랫폼 내부용) — signstage-docs business/platform-order-partner-subcontract-review.md
 * 11장 8단계. 발주 응답과 검수 결과에서 계산하고, 평점은 검수 승인 때 입력한 값의 평균이다.
 */
public final class PartnerEvaluationDto {

    private PartnerEvaluationDto() {
    }

    public static final class Response {
        private Response() {
        }

        /**
         * @param acceptanceRatePercent 수락 / (수락 + 거절 + 만료) × 100. 응답한 발주가 없으면 null
         * @param averageRating 입력된 수행 평점(1~5) 평균, 소수 한 자리. 입력이 없으면 null
         */
        @Getter
        @AllArgsConstructor
        public static class PartnerEvaluation {

            private final Long organizationId;
            private final String organizationName;
            private final boolean orderEligible;
            private final long issuedCount;
            private final long acceptedCount;
            private final long rejectedCount;
            private final long expiredCount;
            private final BigDecimal acceptanceRatePercent;
            private final long inspectedCount;
            private final long deemedCount;
            private final long defectCount;
            private final long delayCount;
            private final long deductionCount;
            private final BigDecimal deductionTotal;
            private final BigDecimal averageRating;
            private final long ratedCount;
            private final LocalDateTime lastIssuedAt;

            public static PartnerEvaluation of(
                    Long organizationId,
                    String organizationName,
                    boolean orderEligible,
                    PartnerOrderStats orders,
                    PartnerInspectionStats inspections
            ) {
                long accepted = orders == null ? 0 : orders.acceptedCount();
                long rejected = orders == null ? 0 : orders.rejectedCount();
                long expired = orders == null ? 0 : orders.expiredCount();
                long responded = accepted + rejected + expired;
                BigDecimal acceptanceRate = responded == 0
                        ? null
                        : BigDecimal.valueOf(accepted * 100).divide(BigDecimal.valueOf(responded), 1, RoundingMode.HALF_UP);
                BigDecimal averageRating = inspections == null || inspections.averageRating() == null
                        ? null
                        : BigDecimal.valueOf(inspections.averageRating()).setScale(1, RoundingMode.HALF_UP);
                return new PartnerEvaluation(
                        organizationId,
                        organizationName,
                        orderEligible,
                        orders == null ? 0 : orders.issuedCount(),
                        accepted,
                        rejected,
                        expired,
                        acceptanceRate,
                        inspections == null ? 0 : inspections.approvedCount(),
                        inspections == null ? 0 : inspections.deemedCount(),
                        inspections == null ? 0 : inspections.defectCount(),
                        inspections == null ? 0 : inspections.delayCount(),
                        inspections == null ? 0 : inspections.deductionCount(),
                        inspections == null || inspections.deductionTotal() == null ? BigDecimal.ZERO : inspections.deductionTotal(),
                        averageRating,
                        inspections == null ? 0 : inspections.ratedCount(),
                        orders == null ? null : orders.lastIssuedAt()
                );
            }
        }
    }
}
