package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChange;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChangeLine;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLineType;
import com.eformworks.signstage.backend.feature.order.entity.ScopeChangeRequest;
import com.eformworks.signstage.backend.feature.order.model.ScopeExtras;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 수주 범위 변경 DTO(D08). 플랫폼 응답에는 고객 금액과 발주 금액을 모두 담고, 파트너 응답에는
 * 발주 금액과 수량·일정만 담는다(D14 — 고객 계약금액은 파트너에게 보이지 않는다).
 */
public final class SalesOrderChangeDto {

    private SalesOrderChangeDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** 변경 후 범위 전체 — 옵션 줄을 다시 보낸다. 플랜·권역은 바꾸지 않는다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Propose {

            @NotBlank
            @Size(max = 500)
            private String reason;

            @Valid
            private List<PlatformAdminSalesOrderDto.Request.Option> options = new ArrayList<>();

            /** 카탈로그에 없는 직접 입력 항목. */
            @Valid
            private List<PlatformAdminSalesOrderDto.Request.CustomItem> customItems = new ArrayList<>();

            @PositiveOrZero
            private BigDecimal discountAmount;

            @Size(max = 500)
            private String discountReason;

            @Size(max = 500)
            private String location;

            private LocalDateTime startsAt;

            private LocalDateTime endsAt;

            /** 예비 태블릿·리허설 별도일 인력·숙박(D23·D24·D26). 비워 보내면 지금 값을 그대로 둔다. */
            @PositiveOrZero
            @Max(999)
            private Integer spareTabletCount;

            @PositiveOrZero
            @Max(999)
            private Integer rehearsalStaffPersonDays;

            @PositiveOrZero
            @Max(999)
            private Integer lodgingNights;

            @PositiveOrZero
            @Max(999)
            private Integer lodgingPersons;

            public ScopeExtras scopeExtrasOr(ScopeExtras current) {
                return new ScopeExtras(
                        spareTabletCount != null ? spareTabletCount : current.spareTabletCount(),
                        rehearsalStaffPersonDays != null ? rehearsalStaffPersonDays : current.rehearsalStaffPersonDays(),
                        lodgingNights != null ? lodgingNights : current.lodgingNights(),
                        lodgingPersons != null ? lodgingPersons : current.lodgingPersons()
                );
            }
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RecordCustomerAcceptance {

            @NotBlank
            @Size(max = 100)
            private String acceptedByName;
        }

        /** 플랫폼 취소·파트너 거절 사유. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Reason {

            @NotBlank
            @Size(max = 500)
            private String reason;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SubmitScopeRequest {

            @NotBlank
            @Size(max = 1000)
            private String content;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CloseScopeRequest {

            @NotBlank
            @Size(max = 500)
            private String note;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class AdminChange {

            private final Long id;
            private final String status;
            private final String reason;
            private final Long partnerOrderId;
            private final String partnerOrganizationName;
            private final String currencyCode;
            private final int beforeTabletCount;
            private final int beforeWifiCount;
            private final int beforeOnsiteStaffPersonDays;
            private final BigDecimal beforeSupplyAmount;
            private final BigDecimal beforeTotalAmount;
            private final BigDecimal beforePartnerAmount;
            private final int tabletCount;
            private final int wifiCount;
            private final int onsiteStaffPersonDays;
            private final BigDecimal supplyAmount;
            private final BigDecimal vatAmount;
            private final BigDecimal totalAmount;
            private final String discountReason;
            private final BigDecimal estimatedPartnerAmount;
            private final BigDecimal partnerAmount;
            private final String location;
            private final LocalDateTime startsAt;
            private final LocalDateTime endsAt;
            private final LocalDateTime proposedAt;
            private final String customerAcceptedByName;
            private final LocalDateTime customerAcceptedAt;
            private final boolean needsPartnerConsent;
            private final boolean partnerAccepted;
            private final LocalDateTime partnerRespondedAt;
            private final String partnerRejectionReason;
            private final LocalDateTime appliedAt;
            private final LocalDateTime cancelledAt;
            private final String cancellationReason;
            private final List<PlatformAdminSalesOrderDto.Response.QuoteLine> lines;
            private final ExtrasView beforeExtras;
            private final ExtrasView extras;

            public static AdminChange of(SalesOrderChange change, List<SalesOrderChangeLine> lines) {
                return new AdminChange(
                        change.getId(),
                        change.getStatus().name(),
                        change.getReason(),
                        change.getPartnerOrder() != null ? change.getPartnerOrder().getId() : null,
                        change.getPartnerOrder() != null ? change.getPartnerOrder().getOrganization().getName() : null,
                        change.getSalesOrder().getCurrencyCode(),
                        change.getBeforeTabletCount(),
                        change.getBeforeWifiCount(),
                        change.getBeforeOnsiteStaffPersonDays(),
                        change.getBeforeSupplyAmount(),
                        change.getBeforeTotalAmount(),
                        change.getBeforePartnerAmount(),
                        change.getTabletCount(),
                        change.getWifiCount(),
                        change.getOnsiteStaffPersonDays(),
                        change.getSupplyAmount(),
                        change.getVatAmount(),
                        change.getTotalAmount(),
                        change.getDiscountReason(),
                        change.getEstimatedPartnerAmount(),
                        change.getPartnerAmount(),
                        change.getLocation(),
                        change.getStartsAt(),
                        change.getEndsAt(),
                        change.getCreatedAt(),
                        change.getCustomerAcceptedByName(),
                        change.getCustomerAcceptedAt(),
                        change.needsPartnerConsent(),
                        change.isPartnerAccepted(),
                        change.getPartnerRespondedAt(),
                        change.getPartnerRejectionReason(),
                        change.getAppliedAt(),
                        change.getCancelledAt(),
                        change.getCancellationReason(),
                        lines.stream().map(PlatformAdminSalesOrderDto.Response.QuoteLine::from).toList(),
                        ExtrasView.from(change.beforeScopeExtras()),
                        ExtrasView.from(change.scopeExtras())
                );
            }
        }

        /** 파트너에게 보여주는 변경 — 고객 금액 없이 수량·일정·발주 금액과 옵션 이름·수량만 담는다. */
        @Getter
        @AllArgsConstructor
        public static class PartnerChange {

            private final Long id;
            private final String status;
            private final String reason;
            private final String currencyCode;
            private final int beforeTabletCount;
            private final int beforeWifiCount;
            private final int beforeOnsiteStaffPersonDays;
            private final BigDecimal beforePartnerAmount;
            private final int tabletCount;
            private final int wifiCount;
            private final int onsiteStaffPersonDays;
            private final BigDecimal partnerAmount;
            private final String location;
            private final LocalDateTime startsAt;
            private final LocalDateTime endsAt;
            private final LocalDateTime proposedAt;
            private final boolean customerAccepted;
            private final boolean partnerAccepted;
            private final LocalDateTime partnerRespondedAt;
            private final String partnerRejectionReason;
            /** 파트너가 지금 동의·거절할 수 있는지. */
            private final boolean respondable;
            private final LocalDateTime appliedAt;
            private final LocalDateTime cancelledAt;
            private final String cancellationReason;
            private final List<ScopeOption> options;
            private final ExtrasView beforeExtras;
            private final ExtrasView extras;

            public static PartnerChange of(SalesOrderChange change, List<SalesOrderChangeLine> lines) {
                return new PartnerChange(
                        change.getId(),
                        change.getStatus().name(),
                        change.getReason(),
                        change.getSalesOrder().getCurrencyCode(),
                        change.getBeforeTabletCount(),
                        change.getBeforeWifiCount(),
                        change.getBeforeOnsiteStaffPersonDays(),
                        change.getBeforePartnerAmount(),
                        change.getTabletCount(),
                        change.getWifiCount(),
                        change.getOnsiteStaffPersonDays(),
                        change.getPartnerAmount(),
                        change.getLocation(),
                        change.getStartsAt(),
                        change.getEndsAt(),
                        change.getCreatedAt(),
                        change.isCustomerAccepted(),
                        change.isPartnerAccepted(),
                        change.getPartnerRespondedAt(),
                        change.getPartnerRejectionReason(),
                        change.isProposed() && change.getPartnerRespondedAt() == null,
                        change.getAppliedAt(),
                        change.getCancelledAt(),
                        change.getCancellationReason(),
                        lines.stream()
                                .filter(line -> line.getLineType() == SalesOrderLineType.UNIT_PRODUCT
                                        || line.getLineType() == SalesOrderLineType.CUSTOM_ITEM)
                                .map(line -> new ScopeOption(line.getName(), line.getQuantity()))
                                .toList(),
                        ExtrasView.from(change.beforeScopeExtras()),
                        ExtrasView.from(change.scopeExtras())
                );
            }
        }

        /** 예비 태블릿·리허설 별도일 인력·숙박(D23·D24·D26). */
        @Getter
        @AllArgsConstructor
        public static class ExtrasView {

            private final int spareTabletCount;
            private final int rehearsalStaffPersonDays;
            private final int lodgingNights;
            private final int lodgingPersons;

            public static ExtrasView from(ScopeExtras extras) {
                return new ExtrasView(
                        extras.spareTabletCount(), extras.rehearsalStaffPersonDays(), extras.lodgingNights(), extras.lodgingPersons()
                );
            }
        }

        @Getter
        @AllArgsConstructor
        public static class ScopeOption {

            private final String name;
            private final int quantity;
        }

        @Getter
        @AllArgsConstructor
        public static class ScopeRequestSummary {

            private final Long id;
            private final Long partnerOrderId;
            private final String organizationName;
            private final String content;
            private final LocalDateTime requestedAt;
            private final LocalDateTime closedAt;
            private final String closingNote;
            private final Long salesOrderChangeId;

            public static ScopeRequestSummary from(ScopeChangeRequest request) {
                return new ScopeRequestSummary(
                        request.getId(),
                        request.getPartnerOrder().getId(),
                        request.getPartnerOrder().getOrganization().getName(),
                        request.getContent(),
                        request.getRequestedAt(),
                        request.getClosedAt(),
                        request.getClosingNote(),
                        request.getSalesOrderChange() != null ? request.getSalesOrderChange().getId() : null
                );
            }
        }

        @Getter
        @AllArgsConstructor
        public static class AdminOverview {

            private final List<AdminChange> changes;
            private final List<ScopeRequestSummary> requests;
        }

        @Getter
        @AllArgsConstructor
        public static class PartnerOverview {

            private final List<PartnerChange> changes;
            private final List<ScopeRequestSummary> requests;
        }
    }
}
