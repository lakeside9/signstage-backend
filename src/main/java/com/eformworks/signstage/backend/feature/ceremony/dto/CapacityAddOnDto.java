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

public final class CapacityAddOnDto {

    private CapacityAddOnDto() {
    }

    public static final class Request {

        private Request() {
        }

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

            @NotNull
            private BigDecimal supplyPrice;

            @NotNull
            private BigDecimal salePrice;

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;

            private String taxCode;

            /** 기존 KRW API 호출/테스트 호환 생성자. */
            public CreateCapacityAddOn(
                    String capacityType,
                    Integer unitAmount,
                    String secondaryCapacityType,
                    Integer secondaryUnitAmount,
                    BigDecimal supplyPrice,
                    BigDecimal salePrice,
                    String discountType,
                    BigDecimal discountValue
            ) {
                this(capacityType, unitAmount, secondaryCapacityType, secondaryUnitAmount, null,
                        supplyPrice, salePrice, discountType, discountValue, null);
            }
        }

        /**
         * {@code capacityType}/{@code secondaryCapacityType}는 상품의 종류를 규정하는 값이라
         * 생성 후 불변이라 {@link CreateCapacityAddOn}과 달리 여기엔 없다 — 플랫폼 관리자
         * 카탈로그 관리 화면 결정. 묶음 상품의 보조 수량({@code secondaryUnitAmount})은 주
         * 수량처럼 수정할 수 있다 — 원래 단일 상품(생성 시 보조 없음)이었다면 계속 null이다.
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

            private String currencyCode;

            @NotNull
            private BigDecimal supplyPrice;

            @NotNull
            private BigDecimal salePrice;

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;

            private String taxCode;

            /** 사용여부. false면 새 추가구매 대상에서 제외된다. */
            @NotNull
            private Boolean active;

            /** 기존 KRW API 호출/테스트 호환 생성자. */
            public UpdateCapacityAddOn(
                    Integer unitAmount,
                    Integer secondaryUnitAmount,
                    BigDecimal supplyPrice,
                    BigDecimal salePrice,
                    String discountType,
                    BigDecimal discountValue,
                    Boolean active
            ) {
                this(unitAmount, secondaryUnitAmount, null, supplyPrice, salePrice,
                        discountType, discountValue, null, active);
            }
        }
    }

    public static final class Response {

        private Response() {
        }

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
        }

        /** 용량 추가구매 상품 값/사용여부 변경 이력 한 행 — 그 변경 시점의 전체 상태 스냅샷이다. */
        @Getter
        @AllArgsConstructor
        public static class CapacityAddOnHistorySummary {

            private final Long id;
            private final String capacityType;
            private final Integer unitAmount;
            private final String secondaryCapacityType;
            private final Integer secondaryUnitAmount;
            private final String currencyCode;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String discountType;
            private final BigDecimal discountValue;
            private final String taxCode;
            private final Boolean active;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }
    }
}
