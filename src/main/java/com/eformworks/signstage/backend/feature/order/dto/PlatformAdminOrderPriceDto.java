package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.CustomerPrice;
import com.eformworks.signstage.backend.feature.order.entity.PartnerRateCard;
import com.eformworks.signstage.backend.feature.order.entity.RegionSurcharge;
import com.eformworks.signstage.backend.feature.order.entity.ServiceRegion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 고객 가격표·지역 할증·파트너 단가표 API DTO(signstage-docs
 * business/platform-order-partner-subcontract-review.md 6장). 통화와 세금 코드를 생략하면 KRW와
 * {@code KR_VAT_STANDARD}를 쓴다.
 */
public final class PlatformAdminOrderPriceDto {

    private PlatformAdminOrderPriceDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** {@code billingPlanId}와 {@code unitProductId} 중 하나만 지정한다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateCustomerPrice {

            private Long billingPlanId;

            private Long unitProductId;

            @Size(min = 3, max = 3)
            private String currencyCode;

            @NotNull
            @PositiveOrZero
            private BigDecimal unitPrice;

            @Size(max = 50)
            private String taxCode;

            @NotNull
            private LocalDate effectiveFrom;

            private LocalDate effectiveTo;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateRegionSurcharge {

            @NotNull
            private ServiceRegion region;

            @Size(min = 3, max = 3)
            private String currencyCode;

            @NotNull
            @PositiveOrZero
            private BigDecimal customerAmount;

            @NotNull
            @PositiveOrZero
            private BigDecimal partnerAmount;

            @Size(max = 50)
            private String taxCode;

            @NotNull
            private LocalDate effectiveFrom;

            private LocalDate effectiveTo;
        }

        /** {@code organizationId}가 없으면 기본 단가표다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreatePartnerRateCard {

            private Long organizationId;

            @Size(min = 3, max = 3)
            private String currencyCode;

            @NotNull
            @PositiveOrZero
            private BigDecimal baseFee;

            @NotNull
            @PositiveOrZero
            private BigDecimal tabletUnitRate;

            @NotNull
            @PositiveOrZero
            private BigDecimal onsiteStaffDayRate;

            @NotNull
            private LocalDate effectiveFrom;

            private LocalDate effectiveTo;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ClosePeriod {

            @NotNull
            private LocalDate effectiveTo;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class AdminCustomerPrice {

            private final Long id;
            private final Long billingPlanId;
            private final String billingPlanName;
            private final Long unitProductId;
            private final String unitProductName;
            private final String currencyCode;
            private final BigDecimal unitPrice;
            private final String taxCode;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;

            public static AdminCustomerPrice from(CustomerPrice price) {
                return new AdminCustomerPrice(
                        price.getId(),
                        price.getBillingPlan() != null ? price.getBillingPlan().getId() : null,
                        price.getBillingPlan() != null ? price.getBillingPlan().getName() : null,
                        price.getUnitProduct() != null ? price.getUnitProduct().getId() : null,
                        price.getUnitProduct() != null ? price.getUnitProduct().getName() : null,
                        price.getCurrencyCode(),
                        price.getUnitPrice(),
                        price.getTaxCode(),
                        price.getPeriod().getEffectiveFrom(),
                        price.getPeriod().getEffectiveTo()
                );
            }
        }

        @Getter
        @AllArgsConstructor
        public static class AdminRegionSurcharge {

            private final Long id;
            private final String region;
            private final String currencyCode;
            private final BigDecimal customerAmount;
            private final BigDecimal partnerAmount;
            private final String taxCode;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;

            public static AdminRegionSurcharge from(RegionSurcharge surcharge) {
                return new AdminRegionSurcharge(
                        surcharge.getId(),
                        surcharge.getRegion().name(),
                        surcharge.getCurrencyCode(),
                        surcharge.getCustomerAmount(),
                        surcharge.getPartnerAmount(),
                        surcharge.getTaxCode(),
                        surcharge.getPeriod().getEffectiveFrom(),
                        surcharge.getPeriod().getEffectiveTo()
                );
            }
        }

        @Getter
        @AllArgsConstructor
        public static class AdminPartnerRateCard {

            private final Long id;
            private final Long organizationId;
            private final String organizationName;
            private final String currencyCode;
            private final BigDecimal baseFee;
            private final BigDecimal tabletUnitRate;
            private final BigDecimal onsiteStaffDayRate;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;

            public static AdminPartnerRateCard from(PartnerRateCard rateCard) {
                return new AdminPartnerRateCard(
                        rateCard.getId(),
                        rateCard.getOrganization() != null ? rateCard.getOrganization().getId() : null,
                        rateCard.getOrganization() != null ? rateCard.getOrganization().getName() : null,
                        rateCard.getCurrencyCode(),
                        rateCard.getBaseFee(),
                        rateCard.getTabletUnitRate(),
                        rateCard.getOnsiteStaffDayRate(),
                        rateCard.getPeriod().getEffectiveFrom(),
                        rateCard.getPeriod().getEffectiveTo()
                );
            }
        }
    }
}
