package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 파트너가 받은 발주 DTO. 고객 계약금액·고객 사업자 정보는 보내지 않는다(signstage-docs
 * business/platform-order-partner-subcontract-review.md D14) — 일정·장소·범위, 고객 담당자 연락처,
 * 발주 금액 내역만 보여준다.
 */
public final class PartnerOrderDto {

    private PartnerOrderDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Reject {

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
        public static class PartnerOrderSummary {

            private final Long id;
            private final String title;
            private final String region;
            private final String location;
            private final LocalDateTime startsAt;
            private final LocalDateTime endsAt;
            private final String currencyCode;
            private final BigDecimal totalAmount;
            private final String status;
            private final LocalDateTime responseDueAt;
            private final LocalDateTime issuedAt;
            private final Long ceremonyId;

            public static PartnerOrderSummary from(PartnerOrder partnerOrder, LocalDateTime now) {
                SalesOrder salesOrder = partnerOrder.getSalesOrder();
                return new PartnerOrderSummary(
                        partnerOrder.getId(),
                        salesOrder.getTitle(),
                        salesOrder.getRegion().name(),
                        salesOrder.getLocation(),
                        salesOrder.getStartsAt(),
                        salesOrder.getEndsAt(),
                        partnerOrder.getCurrencyCode(),
                        partnerOrder.getTotalAmount(),
                        partnerOrder.effectiveStatus(now).name(),
                        partnerOrder.getResponseDueAt(),
                        partnerOrder.getIssuedAt(),
                        partnerOrder.getCeremony() != null ? partnerOrder.getCeremony().getId() : null
                );
            }
        }

        @Getter
        @AllArgsConstructor
        public static class PartnerOrderDetail {

            private final PartnerOrderSummary summary;
            private final String billingPlanName;
            private final List<ScopeOption> options;
            private final String customerName;
            private final String customerContactName;
            private final String customerContactPhone;
            private final String customerContactEmail;
            private final BigDecimal baseFee;
            private final int tabletCount;
            private final BigDecimal tabletUnitRate;
            private final int onsiteStaffPersonDays;
            private final BigDecimal onsiteStaffDayRate;
            private final BigDecimal regionSurcharge;
            private final BigDecimal adjustmentAmount;
            private final String adjustmentReason;
            private final LocalDateTime respondedAt;
            private final String rejectionReason;

            public static PartnerOrderDetail of(PartnerOrder partnerOrder, List<SalesOrderLine> optionLines, LocalDateTime now) {
                SalesOrder salesOrder = partnerOrder.getSalesOrder();
                return new PartnerOrderDetail(
                        PartnerOrderSummary.from(partnerOrder, now),
                        salesOrder.getBillingPlan().getName(),
                        optionLines.stream().map(ScopeOption::from).toList(),
                        salesOrder.getCustomer().getName(),
                        salesOrder.getCustomer().getContactName(),
                        salesOrder.getCustomer().getContactPhone(),
                        salesOrder.getCustomer().getContactEmail(),
                        partnerOrder.getBaseFee(),
                        partnerOrder.getTabletCount(),
                        partnerOrder.getTabletUnitRate(),
                        partnerOrder.getOnsiteStaffPersonDays(),
                        partnerOrder.getOnsiteStaffDayRate(),
                        partnerOrder.getRegionSurcharge(),
                        partnerOrder.getAdjustmentAmount(),
                        partnerOrder.getAdjustmentReason(),
                        partnerOrder.getRespondedAt(),
                        partnerOrder.getRejectionReason()
                );
            }
        }

        /** 플랜 외 추가 범위(단위 상품과 수량). 고객 가격은 담지 않는다. */
        @Getter
        @AllArgsConstructor
        public static class ScopeOption {

            private final Long unitProductId;
            private final String name;
            private final int quantity;

            public static ScopeOption from(SalesOrderLine line) {
                return new ScopeOption(line.getUnitProduct().getId(), line.getName(), line.getQuantity());
            }
        }
    }
}
