package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLine;
import com.eformworks.signstage.backend.feature.order.entity.ServiceRegion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class PlatformAdminSalesOrderDto {

    private PlatformAdminSalesOrderDto() {
    }

    public static final class Request {
        private Request() {
        }

        /**
         * 견적 내용. 수정할 때도 전체를 다시 보낸다(옵션 줄을 지우고 다시 계산한다).
         * {@code quoteValidUntil}을 생략하면 오늘부터 30일이다.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SaveQuote {

            @NotBlank
            @Size(max = 200)
            private String title;

            @NotNull
            private Long billingPlanId;

            @NotNull
            private ServiceRegion region;

            @Size(max = 500)
            private String location;

            private LocalDateTime startsAt;

            private LocalDateTime endsAt;

            @Valid
            private List<Option> options = new ArrayList<>();

            @PositiveOrZero
            private BigDecimal discountAmount;

            @Size(max = 500)
            private String discountReason;

            private LocalDate quoteValidUntil;

            @Size(max = 1000)
            private String memo;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateSalesOrder {

            @NotNull
            private Long customerId;

            @Valid
            @NotNull
            private SaveQuote quote;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Option {

            @NotNull
            private Long unitProductId;

            @Positive
            private int quantity;
        }

        /** 메일·전화로 받은 고객 수락을 플랫폼이 대신 기록한다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RecordAcceptance {

            @NotBlank
            @Size(max = 100)
            private String acceptedByName;
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

        @Getter
        @AllArgsConstructor
        public static class AdminSalesOrderSummary {

            private final Long id;
            private final Long customerId;
            private final String customerName;
            private final String title;
            private final Long billingPlanId;
            private final String region;
            private final LocalDateTime startsAt;
            private final String currencyCode;
            private final BigDecimal supplyAmount;
            private final BigDecimal totalAmount;
            private final BigDecimal estimatedPartnerAmount;
            private final String status;
            private final LocalDate quoteValidUntil;
            private final LocalDateTime acceptedAt;
            private final LocalDateTime createdAt;

            public static AdminSalesOrderSummary from(SalesOrder salesOrder) {
                return new AdminSalesOrderSummary(
                        salesOrder.getId(),
                        salesOrder.getCustomer().getId(),
                        salesOrder.getCustomer().getName(),
                        salesOrder.getTitle(),
                        salesOrder.getBillingPlan().getId(),
                        salesOrder.getRegion().name(),
                        salesOrder.getStartsAt(),
                        salesOrder.getCurrencyCode(),
                        salesOrder.getSupplyAmount(),
                        salesOrder.getTotalAmount(),
                        salesOrder.getEstimatedPartnerAmount(),
                        salesOrder.getStatus().name(),
                        salesOrder.getQuoteValidUntil(),
                        salesOrder.getAcceptedAt(),
                        salesOrder.getCreatedAt()
                );
            }
        }

        @Getter
        @AllArgsConstructor
        public static class AdminSalesOrderDetail {

            private final Long id;
            private final Long customerId;
            private final String customerName;
            private final String title;
            private final Long billingPlanId;
            private final String billingPlanName;
            private final String region;
            private final String location;
            private final LocalDateTime startsAt;
            private final LocalDateTime endsAt;
            private final String currencyCode;
            private final Short currencyFractionDigits;
            private final int tabletCount;
            private final int onsiteStaffPersonDays;
            private final BigDecimal supplyAmount;
            private final BigDecimal vatAmount;
            private final BigDecimal totalAmount;
            private final String discountReason;
            private final BigDecimal estimatedPartnerAmount;
            private final String status;
            private final LocalDate quoteValidUntil;
            private final String quoteAccessToken;
            private final LocalDateTime acceptedAt;
            private final String acceptedByName;
            private final String acceptanceChannel;
            private final LocalDateTime cancelledAt;
            private final String cancellationReason;
            private final String memo;
            private final List<QuoteLine> lines;
            private final List<PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary> partnerOrders;

            public static AdminSalesOrderDetail of(
                    SalesOrder salesOrder,
                    List<SalesOrderLine> lines,
                    List<PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary> partnerOrders
            ) {
                return new AdminSalesOrderDetail(
                        salesOrder.getId(),
                        salesOrder.getCustomer().getId(),
                        salesOrder.getCustomer().getName(),
                        salesOrder.getTitle(),
                        salesOrder.getBillingPlan().getId(),
                        salesOrder.getBillingPlan().getName(),
                        salesOrder.getRegion().name(),
                        salesOrder.getLocation(),
                        salesOrder.getStartsAt(),
                        salesOrder.getEndsAt(),
                        salesOrder.getCurrencyCode(),
                        salesOrder.getCurrencyFractionDigits(),
                        salesOrder.getTabletCount(),
                        salesOrder.getOnsiteStaffPersonDays(),
                        salesOrder.getSupplyAmount(),
                        salesOrder.getVatAmount(),
                        salesOrder.getTotalAmount(),
                        salesOrder.getDiscountReason(),
                        salesOrder.getEstimatedPartnerAmount(),
                        salesOrder.getStatus().name(),
                        salesOrder.getQuoteValidUntil(),
                        salesOrder.getQuoteAccessToken(),
                        salesOrder.getAcceptedAt(),
                        salesOrder.getAcceptedByName(),
                        salesOrder.getAcceptanceChannel() != null ? salesOrder.getAcceptanceChannel().name() : null,
                        salesOrder.getCancelledAt(),
                        salesOrder.getCancellationReason(),
                        salesOrder.getMemo(),
                        lines.stream().map(QuoteLine::from).toList(),
                        partnerOrders
                );
            }
        }

        @Getter
        @AllArgsConstructor
        public static class QuoteLine {

            private final String lineType;
            private final Long unitProductId;
            private final String name;
            private final int quantity;
            private final BigDecimal unitPrice;
            private final BigDecimal amount;
            private final BigDecimal taxRatePercent;
            private final BigDecimal taxAmount;

            public static QuoteLine from(SalesOrderLine line) {
                return new QuoteLine(
                        line.getLineType().name(),
                        line.getUnitProduct() != null ? line.getUnitProduct().getId() : null,
                        line.getName(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getAmount(),
                        line.getTaxRatePercent(),
                        line.getTaxAmount()
                );
            }
        }
    }
}
