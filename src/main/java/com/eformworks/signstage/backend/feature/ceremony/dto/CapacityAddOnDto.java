package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class CapacityAddOnDto {

    private CapacityAddOnDto() {
    }

    public static final class Request {

        private Request() {
        }

        /**
         * 용량 추가구매 상품 생성은 정체성(capacityType 등)과 최초 판매가격 기간을 함께 만든다 —
         * 모든 상품은 최소 1개의 {@code CapacityAddOnPricePeriod}를 가져야 하기 때문이다
         * (signstage-docs business/billing-catalog-price-validity-period-review.md 결정,
         * 2026-09-09).
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateCapacityAddOn {

            @NotBlank
            private String capacityType;

            @NotNull
            @Min(1)
            private Integer unitAmount;

            /** 이 상품이 동시에 늘리는 두 번째 용량 유형. 생략하면(null) 단일 상품 — secondaryUnitAmount와 함께 있거나 함께 없어야 한다. */
            private String secondaryCapacityType;

            @Min(1)
            private Integer secondaryUnitAmount;

            private String currencyCode;

            /** nullable — 원가 미상 상태를 표현할 수 있다(signstage-docs business/billing-catalog-zero-base-schema-redesign-review.md 결정, 2026-09-08, 항목 G). */
            private BigDecimal supplyPrice;

            @NotNull
            private BigDecimal salePrice;

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;

            private String taxCode;

            /** 최초 기간의 사용여부(보통 true). */
            @NotNull
            private Boolean active;

            /** 최초 기간의 시작일. */
            @NotNull
            private LocalDate effectiveFrom;

            /** 최초 기간의 종료일(무기한이면 생략). */
            private LocalDate effectiveTo;
        }

        /**
         * {@code capacityType}/{@code secondaryCapacityType}는 상품의 종류를 규정하는 값이라
         * 생성 후 불변이라 {@link CreateCapacityAddOn}과 달리 여기엔 없다 — 플랫폼 관리자
         * 카탈로그 관리 화면 결정. 묶음 상품의 보조 수량({@code secondaryUnitAmount})은 주
         * 수량처럼 수정할 수 있다 — 원래 단일 상품(생성 시 보조 없음)이었다면 계속 null이다.
         * 가격/사용여부/판매기간은 여기서 다루지 않는다 — {@link CreatePeriod}/{@link UpdatePeriod}
         * 기간 단위 API로 관리한다.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateCapacityAddOn {

            @NotNull
            @Min(1)
            private Integer unitAmount;

            @Min(1)
            private Integer secondaryUnitAmount;
        }

        /** 판매가격 기간 하나를 새로 추가한다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreatePeriod {

            private String currencyCode;
            private BigDecimal supplyPrice;

            @NotNull
            private BigDecimal salePrice;

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;

            private String taxCode;

            @NotNull
            private Boolean active;

            @NotNull
            private LocalDate effectiveFrom;

            private LocalDate effectiveTo;
        }

        /** 이미 있는 판매가격 기간 하나를 고친다({@link CreatePeriod}와 같은 필드). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdatePeriod {

            private String currencyCode;
            private BigDecimal supplyPrice;

            @NotNull
            private BigDecimal salePrice;

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;

            private String taxCode;

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

        /**
         * "오늘" 기준 유효한 판매가격 기간({@code findEffective})을 같이 보여준다. 기간 사이 공백으로
         * 오늘 유효한 기간이 없으면 가격 관련 필드는 전부 null이고 {@code periodStatus}가
         * "NO_ACTIVE_PERIOD"다.
         */
        @Getter
        @AllArgsConstructor
        public static class CapacityAddOnSummary {

            private final Long id;
            private final String capacityType;
            private final Integer unitAmount;
            /** 묶음 상품이면 두 번째로 늘어나는 용량 유형. 단일 상품이면 null. */
            private final String secondaryCapacityType;
            private final Integer secondaryUnitAmount;
            private final String currencyCode;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String discountType;
            private final BigDecimal discountValue;
            private final String taxCode;
            private final Boolean active;
            /** 이 상품을 승인받아 쓰는 구매 건수 — 카탈로그 관리 화면의 "사용 중" 경고용. */
            private final Long usageCount;
            private final LocalDateTime createdAt;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;
            private final String periodStatus;
        }

        /** 용량 추가구매 상품 단위수량 변경 이력 한 행(가격/사용여부는 판매가격 기간 이력 참고). */
        @Getter
        @AllArgsConstructor
        public static class CapacityAddOnHistorySummary {

            private final Long id;
            private final String capacityType;
            private final Integer unitAmount;
            private final String secondaryCapacityType;
            private final Integer secondaryUnitAmount;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }

        /** 판매가격 기간 목록/상세 화면 한 행. */
        @Getter
        @AllArgsConstructor
        public static class CapacityAddOnPeriodSummary {

            private final Long id;
            private final String currencyCode;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String discountType;
            private final BigDecimal discountValue;
            private final String taxCode;
            private final Boolean active;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;
            private final String status;
            private final LocalDateTime createdAt;
        }

        /** 판매가격 기간의 생성/수정/삭제 이력 한 행. */
        @Getter
        @AllArgsConstructor
        public static class CapacityAddOnPeriodHistorySummary {

            private final Long id;
            private final String currencyCode;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String discountType;
            private final BigDecimal discountValue;
            private final String taxCode;
            private final Boolean active;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;
            private final Boolean removed;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }
    }
}
