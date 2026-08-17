package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class CeremonyDto {

    private CeremonyDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateCeremony {

            /** 필수 — signstage-docs business/ceremony-billing-options-review.md 4.10절 결정. */
            @NotNull
            private Long billingPlanId;

            @NotBlank
            private String title;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PurchaseCapacity {

            @NotNull
            private Long capacityAddOnId;

            @NotNull
            @Min(1)
            private Integer quantity;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PurchaseOptionalFeature {

            @NotNull
            private Long optionalFeatureId;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class CeremonySummary {

            private final Long id;
            private final Long organizationId;
            private final Long billingPlanId;
            private final String title;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class CapacityPurchaseSummary {

            private final Long id;
            private final Long ceremonyId;
            private final Long capacityAddOnId;
            private final Integer quantity;
            private final BigDecimal purchasedSalePrice;
            private final String purchasedDiscountType;
            private final BigDecimal purchasedDiscountValue;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class OptionalFeaturePurchaseSummary {

            private final Long id;
            private final Long ceremonyId;
            private final Long optionalFeatureId;
            private final BigDecimal purchasedSalePrice;
            private final String purchasedDiscountType;
            private final BigDecimal purchasedDiscountValue;
            private final LocalDateTime createdAt;
        }
    }
}
