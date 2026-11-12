package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.InspectionDeductionType;
import com.eformworks.signstage.backend.feature.order.entity.OrderInspection;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 검수 DTO — 플랫폼 검수 화면, 지급 건 항목, 파트너 발주 상세에서 같이 쓴다. 발주 금액(파트너가 받는
 * 금액)만 담고 고객 계약금액은 담지 않는다(D14).
 */
public final class OrderInspectionDto {

    private OrderInspectionDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** 감액이 0보다 크면 사유가 필요하다(D13). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Approve {

            @Size(max = 1000)
            private String defectNote;

            @Size(max = 1000)
            private String delayNote;

            private BigDecimal deductionAmount;

            @Size(max = 500)
            private String deductionReason;

            /** 수행 평점 1~5(선택). */
            @Min(1)
            @Max(5)
            private Integer performanceRating;

            /** 감액 유형(계약 기준표). 감액이 있으면 필수다. */
            private InspectionDeductionType deductionType;
        }

        /** 파트너의 감액 이의 제기(D13). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Dispute {

            @NotBlank
            @Size(max = 1000)
            private String reason;
        }

        /** 이의 조정 — 최종 감액을 다시 정한다. 감액이 있으면 사유·유형이 필요하다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ResolveDispute {

            @NotNull
            @PositiveOrZero
            private BigDecimal deductionAmount;

            @Size(max = 500)
            private String deductionReason;

            private InspectionDeductionType deductionType;

            @NotBlank
            @Size(max = 1000)
            private String resolutionNote;
        }
    }

    public static final class Response {
        private Response() {
        }

        /** {@code overdue}는 기한이 지났지만 아직 승인 간주 처리 전인 대기 건이다. */
        @Getter
        @AllArgsConstructor
        public static class InspectionSummary {

            private final Long id;
            private final Long partnerOrderId;
            private final Long salesOrderId;
            private final String title;
            private final LocalDateTime startsAt;
            private final LocalDateTime endsAt;
            private final Long organizationId;
            private final String organizationName;
            private final Long ceremonyId;
            private final LocalDateTime ceremonyCompletedAt;
            private final LocalDate dueDate;
            private final String status;
            private final boolean overdue;
            private final String approvalType;
            private final String currencyCode;
            private final BigDecimal orderAmount;
            private final String defectNote;
            private final String delayNote;
            private final BigDecimal deductionAmount;
            private final String deductionReason;
            private final String deductionType;
            private final Integer performanceRating;
            private final BigDecimal payableAmount;
            private final LocalDate approvedDate;
            private final LocalDateTime approvedAt;
            private final Long partnerPayoutId;
            private final String disputeStatus;
            private final String disputeReason;
            private final LocalDateTime disputedAt;
            /** 이의 제기 마감일(승인일 + 이의 기간). 감액이 없으면 null. */
            private final LocalDate disputeDeadline;
            /** 지금 파트너가 이의를 제기할 수 있는지. */
            private final boolean disputable;
            private final String disputeResolutionNote;
            private final LocalDateTime disputeResolvedAt;

            public static InspectionSummary from(OrderInspection inspection, LocalDate today, int disputeDays) {
                return from(inspection, today, disputeDays, true);
            }

            /** 파트너에게 보낼 때는 수행 평점을 빼고 만든다 — 평점은 플랫폼 내부용이다(11장 8단계). */
            public static InspectionSummary forPartner(OrderInspection inspection, LocalDate today, int disputeDays) {
                return from(inspection, today, disputeDays, false);
            }

            private static InspectionSummary from(
                    OrderInspection inspection, LocalDate today, int disputeDays, boolean includeRating
            ) {
                PartnerOrder partnerOrder = inspection.getPartnerOrder();
                SalesOrder salesOrder = partnerOrder.getSalesOrder();
                return new InspectionSummary(
                        inspection.getId(),
                        partnerOrder.getId(),
                        salesOrder.getId(),
                        salesOrder.getTitle(),
                        salesOrder.getStartsAt(),
                        salesOrder.getEndsAt(),
                        inspection.getOrganization().getId(),
                        inspection.getOrganization().getName(),
                        partnerOrder.getCeremony() != null ? partnerOrder.getCeremony().getId() : null,
                        inspection.getCeremonyCompletedAt(),
                        inspection.getDueDate(),
                        inspection.getStatus().name(),
                        inspection.isOverdue(today),
                        inspection.getApprovalType() != null ? inspection.getApprovalType().name() : null,
                        partnerOrder.getCurrencyCode(),
                        partnerOrder.getTotalAmount(),
                        inspection.getDefectNote(),
                        inspection.getDelayNote(),
                        inspection.getDeductionAmount(),
                        inspection.getDeductionReason(),
                        inspection.getDeductionType() != null ? inspection.getDeductionType().name() : null,
                        includeRating ? inspection.getPerformanceRating() : null,
                        inspection.getPayableAmount(),
                        inspection.getApprovedDate(),
                        inspection.getApprovedAt(),
                        inspection.getPartnerPayout() != null ? inspection.getPartnerPayout().getId() : null,
                        inspection.getDisputeStatus() != null ? inspection.getDisputeStatus().name() : null,
                        inspection.getDisputeReason(),
                        inspection.getDisputedAt(),
                        inspection.disputeDeadline(disputeDays),
                        inspection.canDispute(today, disputeDays),
                        inspection.getDisputeResolutionNote(),
                        inspection.getDisputeResolvedAt()
                );
            }
        }

        /** 감액 유형과 기준 비율(계약 기준표). 기타는 비율이 없다. */
        @Getter
        @AllArgsConstructor
        public static class DeductionTypeOption {

            private final String code;
            private final String label;
            private final BigDecimal ratePercent;

            public static DeductionTypeOption from(InspectionDeductionType type) {
                return new DeductionTypeOption(type.name(), type.label(), type.ratePercent());
            }
        }
    }
}
