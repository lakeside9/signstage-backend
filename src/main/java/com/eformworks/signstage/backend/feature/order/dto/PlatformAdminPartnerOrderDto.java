package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.model.PartnerOrderAmount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class PlatformAdminPartnerOrderDto {

    private PlatformAdminPartnerOrderDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** 조정 금액은 음수(감액)도 된다. 0이 아니면 사유가 필요하다(D05). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PreviewPartnerOrder {

            @NotNull
            private Long organizationId;

            private BigDecimal adjustmentAmount;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class IssuePartnerOrder {

            @NotNull
            private Long organizationId;

            @NotNull
            private LocalDateTime responseDueAt;

            private BigDecimal adjustmentAmount;

            @Size(max = 500)
            private String adjustmentReason;

            /** 파트너에게 전할 메모 — 비워 둘 수 있다. */
            @Size(max = 2000)
            private String memo;
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

        /** 발급 뒤 메모 수정. 비우면 메모를 지운다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateMemo {

            @Size(max = 2000)
            private String memo;
        }
    }

    public static final class Response {
        private Response() {
        }

        /** {@code status}는 응답 기한이 지난 ISSUED를 EXPIRED로 보여준다. */
        @Getter
        @AllArgsConstructor
        public static class AdminPartnerOrderSummary {

            private final Long id;
            private final Long salesOrderId;
            private final String salesOrderTitle;
            private final Long organizationId;
            private final String organizationName;
            private final String currencyCode;
            private final BigDecimal baseFee;
            private final int tabletCount;
            private final BigDecimal tabletUnitRate;
            private final int wifiCount;
            private final BigDecimal wifiUnitRate;
            private final int onsiteStaffPersonDays;
            private final BigDecimal onsiteStaffDayRate;
            private final BigDecimal regionSurcharge;
            private final BigDecimal adjustmentAmount;
            private final String adjustmentReason;
            private final BigDecimal totalAmount;
            private final String status;
            private final LocalDateTime responseDueAt;
            private final LocalDateTime issuedAt;
            private final LocalDateTime respondedAt;
            private final String rejectionReason;
            private final LocalDateTime cancelledAt;
            private final String cancellationReason;
            private final Long ceremonyId;
            private final int spareTabletCount;
            private final int rehearsalStaffPersonDays;
            private final int lodgingNights;
            private final int lodgingPersons;
            private final BigDecimal lodgingNightRate;
            private final BigDecimal customItemAmount;
            private final String memo;

            public static AdminPartnerOrderSummary from(PartnerOrder partnerOrder, LocalDateTime now) {
                return new AdminPartnerOrderSummary(
                        partnerOrder.getId(),
                        partnerOrder.getSalesOrder().getId(),
                        partnerOrder.getSalesOrder().getTitle(),
                        partnerOrder.getOrganization().getId(),
                        partnerOrder.getOrganization().getName(),
                        partnerOrder.getCurrencyCode(),
                        partnerOrder.getBaseFee(),
                        partnerOrder.getTabletCount(),
                        partnerOrder.getTabletUnitRate(),
                        partnerOrder.getWifiCount(),
                        partnerOrder.getWifiUnitRate(),
                        partnerOrder.getOnsiteStaffPersonDays(),
                        partnerOrder.getOnsiteStaffDayRate(),
                        partnerOrder.getRegionSurcharge(),
                        partnerOrder.getAdjustmentAmount(),
                        partnerOrder.getAdjustmentReason(),
                        partnerOrder.getTotalAmount(),
                        partnerOrder.effectiveStatus(now).name(),
                        partnerOrder.getResponseDueAt(),
                        partnerOrder.getIssuedAt(),
                        partnerOrder.getRespondedAt(),
                        partnerOrder.getRejectionReason(),
                        partnerOrder.getCancelledAt(),
                        partnerOrder.getCancellationReason(),
                        partnerOrder.getCeremony() != null ? partnerOrder.getCeremony().getId() : null,
                        partnerOrder.getSpareTabletCount(),
                        partnerOrder.getRehearsalStaffPersonDays(),
                        partnerOrder.getLodgingNights(),
                        partnerOrder.getLodgingPersons(),
                        partnerOrder.getLodgingNightRate(),
                        partnerOrder.getCustomItemAmount(),
                        partnerOrder.getMemo()
                );
            }
        }

        /** 발급 전 금액 미리보기. 마진 미달이어도 오류 대신 {@code marginSufficient=false}로 알려준다. */
        @Getter
        @AllArgsConstructor
        public static class PartnerOrderPreview {

            private final Long organizationId;
            private final boolean partnerEligible;
            private final BigDecimal baseFee;
            private final int tabletCount;
            private final BigDecimal tabletUnitRate;
            private final int wifiCount;
            private final BigDecimal wifiUnitRate;
            private final int onsiteStaffPersonDays;
            private final BigDecimal onsiteStaffDayRate;
            private final BigDecimal regionSurcharge;
            private final BigDecimal adjustmentAmount;
            private final BigDecimal totalAmount;
            private final BigDecimal customerSupplyAmount;
            /** 예비 태블릿(D23)·리허설 별도일 인력(D24)·숙박 인·박과 1박 단가(D26). */
            private final int spareTabletCount;
            private final int rehearsalStaffPersonDays;
            private final int lodgingPersonNights;
            private final BigDecimal lodgingNightRate;
            private final BigDecimal customItemAmount;
            private final BigDecimal marginAmount;
            private final boolean marginSufficient;

            public static PartnerOrderPreview of(
                    Long organizationId,
                    boolean partnerEligible,
                    PartnerOrderAmount amount,
                    BigDecimal customerSupplyAmount,
                    boolean marginSufficient
            ) {
                return new PartnerOrderPreview(
                        organizationId,
                        partnerEligible,
                        amount.baseFee(),
                        amount.tabletCount(),
                        amount.tabletUnitRate(),
                        amount.wifiCount(),
                        amount.wifiUnitRate(),
                        amount.onsiteStaffPersonDays(),
                        amount.onsiteStaffDayRate(),
                        amount.regionSurcharge(),
                        amount.adjustmentAmount(),
                        amount.totalAmount(),
                        customerSupplyAmount,
                        amount.spareTabletCount(),
                        amount.rehearsalStaffPersonDays(),
                        amount.lodgingPersonNights(),
                        amount.lodgingNightRate(),
                        amount.customItemAmount(),
                        customerSupplyAmount.subtract(amount.totalAmount()),
                        marginSufficient
                );
            }
        }
    }
}
