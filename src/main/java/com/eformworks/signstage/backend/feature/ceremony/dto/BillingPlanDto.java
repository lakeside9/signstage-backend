package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 과금 플랜 — 단위 상품 묶음 + 전체 할인(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10, 3.3절).
 * 플랜은 더 이상 자기 가격을 갖지 않는다 — "오늘 가격"은
 * {@code Σ(unitProduct.effectivePrice × includedQuantity)}로 조회 시점에 계산되고, 그 합계에
 * 적용할 할인만 {@code BillingPlanDiscountPeriod}로 기간별 관리한다.
 */
public final class BillingPlanDto {

    private BillingPlanDto() {
    }

    public static final class Request {

        private Request() {
        }

        /** 플랜을 구성하는 단위 상품 한 줄 — {@code UnitProduct.id} + 포함 수량 + 구매 가능 여부. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PlanUnitProductLine {

            @NotNull
            private Long unitProductId;

            /** 기본 포함 수량 — 0 이상. 0이면 "기본 미포함, 추가구매로만 확보". */
            @NotNull
            private Integer includedQuantity;

            /** 이 플랜을 쓰는 행사가 이 단위 상품을 추가구매 후보로 고를 수 있는지. */
            @NotNull
            private Boolean purchasable;
        }

        /**
         * 플랜 생성은 정체성(name)과 단위 상품 구성, 최초 할인 기간을 함께 만든다 — 모든 플랜은
         * 최소 1개의 {@code BillingPlanDiscountPeriod}를 가져야 한다(signstage-docs
         * business/billing-catalog-price-validity-period-review.md 결정, 2026-09-09의 원칙을
         * 할인 기간에도 그대로 적용).
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreatePlan {

            @NotBlank
            private String name;

            /** 이 플랜이 포함하는 단위 상품 구성 전체(생략하면 빈 목록). */
            private List<PlanUnitProductLine> unitProducts;

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;

            /** 최초 기간의 사용여부(보통 true). */
            @NotNull
            private Boolean active;

            /**
             * 최초 기간의 시작일. 생략하면(null) 플랫폼 기본 타임존(Asia/Seoul) 기준 오늘로
             * 채운다(signstage-docs
             * business/organization-discount-override-security-and-validity-period-review.md
             * 결정 #5, 2026-09-10).
             */
            private LocalDate effectiveFrom;

            /** 최초 기간의 종료일(무기한이면 생략). */
            private LocalDate effectiveTo;

            /**
             * 구독형 플랜 조건(signstage-docs
             * business/organization-event-discount-pricing-review.md 8장 결정, 2026-09-10) —
             * 4개 모두 생성 후 불변이라 {@link UpdatePlan}에는 없다. 생략하면 일반(STANDARD)
             * 플랜으로 만든다.
             */
            private String planType;

            /** planType이 SUBSCRIPTION일 때만 필수 — PERIOD_AND_COUNT | COUNT_ONLY. */
            private String subscriptionType;

            /** subscriptionType이 PERIOD_AND_COUNT일 때만 필수(6 또는 12) — COUNT_ONLY는 생략. */
            private Integer subscriptionPeriodMonths;

            /** planType이 SUBSCRIPTION일 때만 필수 — 이 플랜으로 만들 수 있는 Ceremony 최대 건수. */
            private Integer subscriptionAllowedCount;
        }

        /**
         * 단위 상품 구성({@code unitProducts})을 여기서 통째로 교체할 수 있다(생략하면 빈 목록 —
         * 전부 뺀다는 뜻, {@link CreatePlan}과 같은 규약). 이미 확정/진행 중인 행사는
         * {@code CeremonyPlanHistoryUnitProduct} 스냅샷으로 보호되어 이 수정에 영향받지 않는다.
         *
         * <p>할인/사용여부/판매기간은 여기서 다루지 않는다 — {@link CreatePeriod}/{@link UpdatePeriod}
         * 기간 단위 API로 관리한다.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdatePlan {

            @NotBlank
            private String name;

            private List<PlanUnitProductLine> unitProducts;
        }

        /** 할인 기간 하나를 새로 추가한다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreatePeriod {

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;

            @NotNull
            private Boolean active;

            /** 생략하면(null) 플랫폼 기본 타임존(Asia/Seoul) 기준 오늘로 채운다({@link CreatePlan}과 같은 규칙). */
            private LocalDate effectiveFrom;

            private LocalDate effectiveTo;
        }

        /** 이미 있는 할인 기간 하나를 고친다({@link CreatePeriod}와 같은 필드). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdatePeriod {

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;

            @NotNull
            private Boolean active;

            @NotNull
            private LocalDate effectiveFrom;

            private LocalDate effectiveTo;
        }
    }

    public static final class Response {

        private Response() {
        }

        /** 플랜이 포함하는 단위 상품 한 줄 — 목록/상세 화면용. */
        @Getter
        @AllArgsConstructor
        public static class PlanUnitProductLineSummary {

            private final Long unitProductId;
            private final String unitProductType;
            private final String unitProductName;
            private final String unitProductCategory;
            private final Integer includedQuantity;
            private final Boolean purchasable;
            /** "오늘" 기준 단위 상품 자체의 판매가(할인 없음) — 플랜 소계 계산에 쓰이는 값 그대로. */
            private final BigDecimal salePrice;
            private final String currencyCode;
        }

        /**
         * 목록 화면용 — "오늘" 기준 유효한 할인 기간({@code findEffective})을 같이 보여준다.
         * 기간 사이 공백으로 오늘 유효한 기간이 없으면 할인 관련 필드는 전부 null이고
         * {@code periodStatus}가 "NO_ACTIVE_PERIOD"다.
         */
        @Getter
        @AllArgsConstructor
        public static class BillingPlanSummary {

            private final Long id;
            private final String name;
            private final List<PlanUnitProductLineSummary> unitProducts;
            /** 이 플랜을 쓰는 행사(Ceremony) 수 — 카탈로그 관리 화면의 "사용 중" 경고용. */
            private final Long usageCount;
            private final LocalDateTime createdAt;

            // 구독형 플랜 조건(생성 후 불변) — STANDARD면 전부 null/false.
            private final String planType;
            private final boolean subscription;
            private final String subscriptionType;
            private final Integer subscriptionPeriodMonths;
            private final Integer subscriptionAllowedCount;

            // 오늘 기준 유효한 할인 기간(없으면 전부 null/NO_ACTIVE_PERIOD).
            private final String discountType;
            private final BigDecimal discountValue;
            private final Boolean active;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;
            private final String periodStatus;
        }

        /** 플랜 이름/단위 상품 구성 변경 이력 한 행(할인/사용여부는 {@link BillingPlanPeriodSummary} 쪽 이력 참고). */
        @Getter
        @AllArgsConstructor
        public static class BillingPlanHistorySummary {

            private final Long id;
            private final String name;
            private final List<PlanUnitProductLineSummary> unitProducts;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }

        /** 할인 기간 목록/상세 화면 한 행. */
        @Getter
        @AllArgsConstructor
        public static class BillingPlanPeriodSummary {

            private final Long id;
            private final String discountType;
            private final BigDecimal discountValue;
            private final Boolean active;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;
            private final String status;
            private final LocalDateTime createdAt;
        }

        /** 할인 기간의 생성/수정/삭제 이력 한 행. */
        @Getter
        @AllArgsConstructor
        public static class BillingPlanPeriodHistorySummary {

            private final Long id;
            private final String discountType;
            private final BigDecimal discountValue;
            private final Boolean active;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;
            private final Boolean removed;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }
    }
}
