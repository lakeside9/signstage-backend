package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 조직×품목 세밀 할인 오버라이드(안 A). signstage-docs
 * business/organization-event-discount-pricing-review.md 4.1절(2026-08-21 재검토) 참고.
 */
public final class OrganizationDiscountDto {

    private OrganizationDiscountDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SetDiscount {

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class BillingPlanDiscountSummary {

            private final Long id;
            private final Long organizationId;
            private final Long billingPlanId;
            private final String billingPlanName;
            private final String discountType;
            private final BigDecimal discountValue;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class OptionalFeatureDiscountSummary {

            private final Long id;
            private final Long organizationId;
            private final Long optionalFeatureId;
            private final String optionalFeatureName;
            private final String discountType;
            private final BigDecimal discountValue;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class CapacityAddOnDiscountSummary {

            private final Long id;
            private final Long organizationId;
            private final Long capacityAddOnId;
            /** SIGNERS/TEMPLATES/TEST_EVENTS/MAIN_EVENTS — 다른 CapacityAddOn 관련 DTO와 같이 name()을 그대로 내려주고, 라벨링은 프런트가 한다. */
            private final String capacityType;
            private final Integer unitAmount;
            private final String discountType;
            private final BigDecimal discountValue;
            private final LocalDateTime createdAt;
        }

        /** 조직별 할인 관리 화면이 한 조직에 걸린 세 카탈로그 종류의 오버라이드를 한 번에 받는 데 쓴다. */
        @Getter
        @AllArgsConstructor
        public static class OrganizationDiscountOverview {

            private final List<BillingPlanDiscountSummary> billingPlanDiscounts;
            private final List<OptionalFeatureDiscountSummary> optionalFeatureDiscounts;
            private final List<CapacityAddOnDiscountSummary> capacityAddOnDiscounts;
        }

        /**
         * 조직×플랜 할인 오버라이드 변경 이력 한 행. 설정(생성/수정) 시점마다, 그리고 제거
         * 시점에(removed=true, 그 직전 값) 한 건씩 쌓인다 — 카탈로그의
         * {@code BillingPlanHistorySummary}와 같은 구조다.
         */
        @Getter
        @AllArgsConstructor
        public static class BillingPlanDiscountHistorySummary {

            private final Long id;
            private final Long organizationId;
            private final Long billingPlanId;
            private final String billingPlanName;
            private final String discountType;
            private final BigDecimal discountValue;
            private final boolean removed;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class OptionalFeatureDiscountHistorySummary {

            private final Long id;
            private final Long organizationId;
            private final Long optionalFeatureId;
            private final String optionalFeatureName;
            private final String discountType;
            private final BigDecimal discountValue;
            private final boolean removed;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class CapacityAddOnDiscountHistorySummary {

            private final Long id;
            private final Long organizationId;
            private final Long capacityAddOnId;
            private final String capacityType;
            private final Integer unitAmount;
            private final String discountType;
            private final BigDecimal discountValue;
            private final boolean removed;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }
    }
}
