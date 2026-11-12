package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.PartnerOffset;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 파트너 귀책 상계 DTO(signstage-docs business/platform-order-partner-subcontract-review.md D13). */
public final class PartnerOffsetDto {

    private PartnerOffsetDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** @param occurredOn 귀책이 확정된 날(고객 환불일 등). 생략하면 오늘이다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Register {

            @NotNull
            private Long partnerOrderId;

            @NotNull
            @Positive
            private BigDecimal amount;

            @NotBlank
            @Size(max = 500)
            private String reason;

            private LocalDate occurredOn;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Cancel {

            @NotBlank
            @Size(max = 500)
            private String reason;
        }
    }

    public static final class Response {
        private Response() {
        }

        /** {@code carriedOver}면 이전 지급액을 넘어 이월된 상계다. */
        @Getter
        @AllArgsConstructor
        public static class OffsetSummary {

            private final Long id;
            private final Long organizationId;
            private final String organizationName;
            private final Long partnerOrderId;
            private final Long salesOrderId;
            private final String title;
            private final String currencyCode;
            private final BigDecimal amount;
            private final String reason;
            private final LocalDate occurredOn;
            private final String status;
            private final Long partnerPayoutId;
            private final boolean carriedOver;
            private final Long carriedOverFromPayoutId;
            private final LocalDateTime cancelledAt;
            private final String cancellationReason;
            private final LocalDateTime createdAt;

            public static OffsetSummary from(PartnerOffset offset) {
                SalesOrder salesOrder = offset.getPartnerOrder().getSalesOrder();
                return new OffsetSummary(
                        offset.getId(),
                        offset.getOrganization().getId(),
                        offset.getOrganization().getName(),
                        offset.getPartnerOrder().getId(),
                        salesOrder.getId(),
                        salesOrder.getTitle(),
                        offset.getCurrencyCode(),
                        offset.getAmount(),
                        offset.getReason(),
                        offset.getOccurredOn(),
                        offset.getStatus().name(),
                        offset.getPartnerPayout() != null ? offset.getPartnerPayout().getId() : null,
                        offset.isCarryOver(),
                        offset.isCarryOver() ? offset.getCarriedOverFromPayout().getId() : null,
                        offset.getCancelledAt(),
                        offset.getCancellationReason(),
                        offset.getCreatedAt()
                );
            }
        }
    }
}
