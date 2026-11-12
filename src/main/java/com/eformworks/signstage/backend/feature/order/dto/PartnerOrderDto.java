package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
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
                        // 수락 후 취소(파트너 교체)된 발주는 행사로 들어가지 못한다 — 행사는 다음 파트너에게 넘어간다.
                        partnerOrder.getStatus() == PartnerOrderStatus.ACCEPTED && partnerOrder.getCeremony() != null
                                ? partnerOrder.getCeremony().getId()
                                : null
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
            private final int wifiCount;
            private final BigDecimal wifiUnitRate;
            private final int onsiteStaffPersonDays;
            private final BigDecimal onsiteStaffDayRate;
            private final BigDecimal regionSurcharge;
            /** 예비 태블릿(D23)·리허설 별도일 인력(D24)·숙박(D26). 단가는 태블릿·인력 단가와 1박 단가다. */
            private final int spareTabletCount;
            private final int rehearsalStaffPersonDays;
            private final int lodgingNights;
            private final int lodgingPersons;
            private final BigDecimal lodgingNightRate;
            /** 직접 입력 항목의 발주 금액 합계 — 품목별 발주 단가는 {@code options}에 있다. */
            private final BigDecimal customItemAmount;
            private final BigDecimal adjustmentAmount;
            private final String adjustmentReason;
            private final LocalDateTime respondedAt;
            private final String rejectionReason;
            /** 행사가 완료돼 검수가 생겼으면 그 상태(D11·D13). 그 전에는 null이다. */
            private final OrderInspectionDto.Response.InspectionSummary inspection;

            public static PartnerOrderDetail of(
                    PartnerOrder partnerOrder,
                    List<SalesOrderLine> optionLines,
                    OrderInspectionDto.Response.InspectionSummary inspection,
                    LocalDateTime now
            ) {
                SalesOrder salesOrder = partnerOrder.getSalesOrder();
                // 응답 대기·수락 중일 때만 고객 담당자 연락처를 보인다 — 거절·만료·취소(파트너 교체)된 파트너에게는 감춘다(D18).
                boolean showsContact = partnerOrder.getStatus() == PartnerOrderStatus.ISSUED
                        || partnerOrder.getStatus() == PartnerOrderStatus.ACCEPTED;
                return new PartnerOrderDetail(
                        PartnerOrderSummary.from(partnerOrder, now),
                        salesOrder.getBillingPlan().getName(),
                        optionLines.stream().map(ScopeOption::from).toList(),
                        salesOrder.getCustomer().getName(),
                        showsContact ? salesOrder.getCustomer().getContactName() : null,
                        showsContact ? salesOrder.getCustomer().getContactPhone() : null,
                        showsContact ? salesOrder.getCustomer().getContactEmail() : null,
                        partnerOrder.getBaseFee(),
                        partnerOrder.getTabletCount(),
                        partnerOrder.getTabletUnitRate(),
                        partnerOrder.getWifiCount(),
                        partnerOrder.getWifiUnitRate(),
                        partnerOrder.getOnsiteStaffPersonDays(),
                        partnerOrder.getOnsiteStaffDayRate(),
                        partnerOrder.getRegionSurcharge(),
                        partnerOrder.getSpareTabletCount(),
                        partnerOrder.getRehearsalStaffPersonDays(),
                        partnerOrder.getLodgingNights(),
                        partnerOrder.getLodgingPersons(),
                        partnerOrder.getLodgingNightRate(),
                        partnerOrder.getCustomItemAmount(),
                        partnerOrder.getAdjustmentAmount(),
                        partnerOrder.getAdjustmentReason(),
                        partnerOrder.getRespondedAt(),
                        partnerOrder.getRejectionReason(),
                        inspection
                );
            }
        }

        /**
         * 플랜 외 추가 범위(단위 상품 옵션과 직접 입력 항목). 고객 가격은 담지 않는다. 직접 입력 항목은
         * {@code unitProductId}가 null이고 발주 단가를 보여준다.
         */
        @Getter
        @AllArgsConstructor
        public static class ScopeOption {

            private final Long unitProductId;
            private final String name;
            private final int quantity;
            private final BigDecimal partnerUnitPrice;

            public static ScopeOption from(SalesOrderLine line) {
                return new ScopeOption(
                        line.getUnitProduct() != null ? line.getUnitProduct().getId() : null, line.getName(), line.getQuantity(),
                        line.getPartnerUnitPrice()
                );
            }
        }
    }
}
