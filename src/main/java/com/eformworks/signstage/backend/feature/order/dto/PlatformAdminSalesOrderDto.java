package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChangeLine;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLine;
import com.eformworks.signstage.backend.feature.order.entity.ServiceRegion;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteCalculation;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteAdjustment;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteCustomItem;
import com.eformworks.signstage.backend.feature.order.model.ScopeExtras;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
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
        /** 예비 태블릿·리허설 인·일·숙박 박수·인원의 상한. */
        static final long MAX_EXTRA_QUANTITY = 999;

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

            /** 카탈로그에 없는 직접 입력 항목. */
            @Valid
            private List<CustomItem> customItems = new ArrayList<>();

            /** 영업 할인 방식. 생략하면 정액(FIXED_AMOUNT)이다. */
            private DiscountType discountType;

            /** 정액 할인 금액 — 정액 방식일 때만 쓴다. */
            @PositiveOrZero
            private BigDecimal discountAmount;

            /** 할인율(%) — 퍼센트 방식일 때만 쓴다. 공급가 소계(할인 전)에 곱한다. */
            @DecimalMin("0")
            @DecimalMax("100")
            private BigDecimal discountRatePercent;

            @Size(max = 500)
            private String discountReason;

            /** 공급가(부가세 별도) 절사 단위 — 절사한 금액에 부가세를 매긴다 — 10·100·1,000·10,000. 생략하면 절사하지 않는다. */
            private Integer truncationUnit;

            /**
             * 견적서 "제공 서비스" 본문. 생략하면(null) 새 견적은 저장일의 견적 공통 문구를, 기존 견적은 지금 문구를 그대로 쓴다.
             * 빈 문자열이면 견적서에 그 칸을 두지 않는다.
             */
            @Size(max = 5000)
            private String serviceDescription;

            /** 예비 태블릿(D23, 발주에만). 생략하면 0이다. */
            @PositiveOrZero
            @Max(MAX_EXTRA_QUANTITY)
            private Integer spareTabletCount;

            /** 본행사와 다른 날 리허설 현장 인력 인·일(D24). 생략하면 0이다. */
            @PositiveOrZero
            @Max(MAX_EXTRA_QUANTITY)
            private Integer rehearsalStaffPersonDays;

            /** 숙박 박수·인원(D26). 함께 넣거나 함께 비운다. */
            @PositiveOrZero
            @Max(MAX_EXTRA_QUANTITY)
            private Integer lodgingNights;

            @PositiveOrZero
            @Max(MAX_EXTRA_QUANTITY)
            private Integer lodgingPersons;

            private LocalDate quoteValidUntil;

            @Size(max = 1000)
            private String memo;

            /** 견적서 하단 안내문구. 생략·빈 문자열 규칙은 {@code serviceDescription}과 같다. */
            @Size(max = 2000)
            private String quoteFooterNotice;

            public ScopeExtras scopeExtras() {
                return ScopeExtras.of(spareTabletCount, rehearsalStaffPersonDays, lodgingNights, lodgingPersons);
            }

            public SalesQuoteAdjustment quoteAdjustment() {
                return SalesQuoteAdjustment.of(discountType, discountAmount, discountRatePercent, truncationUnit);
            }
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

        /** 직접 입력 항목 — 품목명·수량·고객 단가와 발주 단가(생략하면 0, 플랫폼이 처리하는 항목). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CustomItem {

            @NotBlank
            @Size(max = 200)
            private String name;

            @Positive
            @Max(MAX_EXTRA_QUANTITY)
            private int quantity;

            @NotNull
            @PositiveOrZero
            private BigDecimal unitPrice;

            @PositiveOrZero
            private BigDecimal partnerUnitPrice;

            public static List<SalesQuoteCustomItem> toModels(List<CustomItem> items) {
                return items == null
                        ? List.of()
                        : items.stream()
                                .map(item -> new SalesQuoteCustomItem(item.name, item.quantity, item.unitPrice, item.partnerUnitPrice))
                                .toList();
            }
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
            private final int wifiCount;
            private final int onsiteStaffPersonDays;
            private final BigDecimal customItemPartnerAmount;
            private final int spareTabletCount;
            private final int rehearsalStaffPersonDays;
            private final int lodgingNights;
            private final int lodgingPersons;
            private final BigDecimal supplyAmount;
            private final BigDecimal vatAmount;
            private final BigDecimal totalAmount;
            private final String discountReason;
            /** 퍼센트 할인이면 할인율(%), 정액 할인·할인 없음이면 null이다. */
            private final BigDecimal discountRatePercent;
            /** 공급가 절사 단위. 절사하지 않으면 null이다. */
            private final Integer truncationUnit;
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
            /** 견적서 "제공 서비스" 본문(견적 저장 시점 복사본). */
            private final String serviceDescription;
            /** 견적서 하단 안내문구(복사본). */
            private final String quoteFooterNotice;
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
                        salesOrder.getWifiCount(),
                        salesOrder.getOnsiteStaffPersonDays(),
                        salesOrder.getCustomItemPartnerAmount(),
                        salesOrder.getSpareTabletCount(),
                        salesOrder.getRehearsalStaffPersonDays(),
                        salesOrder.getLodgingNights(),
                        salesOrder.getLodgingPersons(),
                        salesOrder.getSupplyAmount(),
                        salesOrder.getVatAmount(),
                        salesOrder.getTotalAmount(),
                        salesOrder.getDiscountReason(),
                        salesOrder.getDiscountRatePercent(),
                        salesOrder.getTruncationUnit(),
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
                        salesOrder.getServiceDescription(),
                        salesOrder.getQuoteFooterNotice(),
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
            /** 직접 입력 줄의 발주 단가 — 관리자 화면에만 보인다. */
            private final BigDecimal partnerUnitPrice;
            /** 견적서 줄 설명(저장된 줄만, 계산 미리보기는 null). */
            private final String description;

            public static QuoteLine from(SalesQuoteCalculation.Line line) {
                return new QuoteLine(
                        line.lineType().name(),
                        line.unitProduct() != null ? line.unitProduct().getId() : null,
                        line.name(),
                        line.quantity(),
                        line.unitPrice(),
                        line.amount(),
                        line.taxRatePercent(),
                        line.taxAmount(),
                        line.partnerUnitPrice(),
                        null
                );
            }

            public static QuoteLine from(SalesOrderChangeLine line) {
                return new QuoteLine(
                        line.getLineType().name(),
                        line.getUnitProduct() != null ? line.getUnitProduct().getId() : null,
                        line.getName(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getAmount(),
                        line.getTaxRatePercent(),
                        line.getTaxAmount(),
                        line.getPartnerUnitPrice(),
                        line.getDescription()
                );
            }

            public static QuoteLine from(SalesOrderLine line) {
                return new QuoteLine(
                        line.getLineType().name(),
                        line.getUnitProduct() != null ? line.getUnitProduct().getId() : null,
                        line.getName(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getAmount(),
                        line.getTaxRatePercent(),
                        line.getTaxAmount(),
                        line.getPartnerUnitPrice(),
                        line.getDescription()
                );
            }
        }
    }
}
