package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.Min;
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
 * {@code OptionalFeatureDto}와 {@code CapacityAddOn}Dto를 통합한 카탈로그 단위 상품 DTO —
 * signstage-docs business/billing-catalog-unit-product-model-redesign-review.md 결정
 * (2026-09-10). 단위 상품은 할인을 갖지 않는다(할인은 오직 BillingPlan에만 존재한다) — 그래서
 * 옛 두 DTO에 있던 {@code discountType}/{@code discountValue} 필드가 여기엔 없다. 옛
 * {@code CapacityAddOn}의 {@code unitAmount}/{@code secondaryCapacityType}/
 * {@code secondaryUnitAmount}(묶음 상품)도 없다 — 묶음은 폐지했다(같은 문서 §3.7 결정).
 */
public final class UnitProductDto {

    private UnitProductDto() {
    }

    public static final class Request {

        private Request() {
        }

        /**
         * 단위 상품 생성은 정체성(type/name/category 등)과 최초 판매가격 기간을 함께 만든다 —
         * 모든 단위 상품은 최소 1개의 {@code UnitProductPricePeriod}를 가져야 하기 때문이다
         * (signstage-docs business/billing-catalog-price-validity-period-review.md 결정,
         * 2026-09-09).
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateUnitProduct {

            @NotBlank
            private String type;

            @NotBlank
            private String name;

            /** 이 상품이 무엇인지 설명하는 자유 텍스트. 생략하면(null) 설명 없음(2026-09-11 사용자 요청). */
            private String description;

            /** 상위 분류(ESSENTIAL/EQUIPMENT/PERSONNEL/APPLICATION). */
            @NotBlank
            private String category;

            /** 같은 값을 가진 다른 단위 상품과 한 CeremonyEvent에 동시 적용할 수 없다. 생략하면(null) 배타 관계 없음. */
            private String exclusivityGroup;

            /**
             * 이 상품을 한 행사에서 추가구매로 누적 살 수 있는 최대 수량. 생략하면(null) 무제한
             * (2026-09-12 사용자 요청). 토글형({@code type='EVENT_EFFECT_BUNDLE'})은 이 값을
             * 보내도 무시된다 — 그 타입은 항상 최대 1로 고정돼 있다({@code UnitProductType#isToggle()}).
             */
            @Min(1)
            private Integer maxPurchaseQuantity;

            private String currencyCode;

            /** nullable — 원가 미상 상태를 표현할 수 있다(signstage-docs business/billing-catalog-zero-base-schema-redesign-review.md 결정, 2026-09-08, 항목 G). */
            private BigDecimal supplyPrice;

            @NotNull
            private BigDecimal salePrice;

            private String taxCode;

            /** 최초 기간의 사용여부(보통 true). */
            @NotNull
            private Boolean active;

            /**
             * 최초 기간의 시작일. 생략하면(null) 플랫폼 기본 타임존(Asia/Seoul) 기준 오늘로
             * 채운다(signstage-docs
             * business/organization-discount-override-security-and-validity-period-review.md
             * 결정 #5, 2026-09-10 — 단위 상품/플랜 카탈로그는 조직·행사 스코프가 없어 조직
             * 타임존 대신 플랫폼 기본값을 쓴다).
             */
            private LocalDate effectiveFrom;

            /** 최초 기간의 종료일(무기한이면 생략). */
            private LocalDate effectiveTo;

            /**
             * 이 묶음이 열어주는 이벤트 효과 목록 — {@code type='EVENT_EFFECT_BUNDLE'}일 때만
             * 의미가 있다(그 외 종류에서 값이 오면 무시하지 않고 거부한다). 생략하면(null)
             * 빈 묶음으로 시작한다(2026-09-08 결정).
             */
            private List<Long> effectDefinitionIds;
        }

        /**
         * {@code type}은 상품의 종류를 규정하는 값이라 생성 후 불변이라 {@link CreateUnitProduct}와
         * 달리 여기엔 없다 — 플랫폼 관리자 카탈로그 관리 화면 결정. 가격/사용여부/판매기간도 여기서
         * 다루지 않는다 — {@link CreatePeriod}/{@link UpdatePeriod} 기간 단위 API로 관리한다.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateUnitProduct {

            @NotBlank
            private String name;

            /** 이 상품이 무엇인지 설명하는 자유 텍스트. null이면 설명 없음(2026-09-11 사용자 요청). */
            private String description;

            @NotBlank
            private String category;

            /** 같은 값을 가진 다른 단위 상품과 한 CeremonyEvent에 동시 적용할 수 없다. null이면 배타 관계 없음. */
            private String exclusivityGroup;

            /**
             * 이 상품을 한 행사에서 추가구매로 누적 살 수 있는 최대 수량. null이면 무제한
             * (2026-09-12 사용자 요청). 토글형은 이 값을 보내도 무시된다({@link CreateUnitProduct}와
             * 같은 이유).
             */
            @Min(1)
            private Integer maxPurchaseQuantity;

            /**
             * 이 묶음이 열어주는 이벤트 효과 목록을 통째로 교체한다(delete-all-then-recreate) —
             * {@code type='EVENT_EFFECT_BUNDLE'}일 때만 의미가 있다. 생략하면(null) 기존 구성을
             * 그대로 둔다. 빈 배열을 명시적으로 보내면 전부 해제한다.
             */
            private List<Long> effectDefinitionIds;
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

            private String taxCode;

            @NotNull
            private Boolean active;

            /** 생략하면(null) 플랫폼 기본 타임존(Asia/Seoul) 기준 오늘로 채운다({@link CreateUnitProduct}와 같은 규칙). */
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
         * "NO_ACTIVE_PERIOD"다. {@link com.eformworks.signstage.backend.feature.ceremony.service.CeremonyService}의
         * 구매 조회 메서드처럼 구매 스냅샷이 있으면 가격 필드를 스냅샷 값으로 덮어써 내려주는
         * 호출부도 있다 — 이 경우 effectiveFrom/effectiveTo/periodStatus는 항상 카탈로그의 "오늘"
         * 기간을 가리킨다(스냅샷과 무관).
         */
        @Getter
        @AllArgsConstructor
        public static class UnitProductSummary {

            private final Long id;
            private final String type;
            private final String name;
            private final String description;
            private final String category;
            private final String exclusivityGroup;
            /** 한 행사에서 추가구매로 누적 살 수 있는 최대 수량. null이면 무제한. 토글형은 항상 null(타입 자체가 최대 1로 고정). */
            private final Integer maxPurchaseQuantity;
            private final String currencyCode;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String taxCode;
            private final Boolean active;
            /** 이 단위 상품을 승인받아 쓰는 구매 건수 — 카탈로그 관리 화면의 "사용 중" 경고용. */
            private final Long usageCount;
            /** 이 묶음이 여는 이벤트 효과 id 목록. {@code type='EVENT_EFFECT_BUNDLE'}가 아니면 항상 빈 배열이다. */
            private final List<Long> effectDefinitionIds;
            private final LocalDateTime createdAt;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;
            private final String periodStatus;
            /** 카탈로그 목록 화면의 표시 순서 — 위/아래 이동 버튼으로 바꾼다(2026-09-10). */
            private final Integer displayOrder;
            /**
             * 삭제 가능 여부 — 플랜 구성(현재/이력 포함)·행사 플랜 스냅샷·추가구매·행사 적용·
             * 이벤트 효과 묶음 매핑 어디에도 한 번도 등장한 적이 없어야 true다
             * (signstage-docs business/billing-catalog-unit-product-model-redesign-review.md
             * 결정, 2026-09-10 삭제 기능 추가). {@code usageCount}보다 훨씬 넓은 범위를 본다 —
             * usageCount는 승인된 구매만 세지만, 이건 어떤 흔적이라도 있으면 false다.
             */
            private final boolean canDelete;
        }

        /** 단위 상품 이름/설명/분류/배타그룹/최대 구매 수량 변경 이력 한 행(가격/사용여부는 판매가격 기간 이력 참고). */
        @Getter
        @AllArgsConstructor
        public static class UnitProductHistorySummary {

            private final Long id;
            private final String type;
            private final String name;
            private final String description;
            private final String category;
            private final String exclusivityGroup;
            private final Integer maxPurchaseQuantity;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }

        /** 판매가격 기간 목록/상세 화면 한 행. */
        @Getter
        @AllArgsConstructor
        public static class UnitProductPeriodSummary {

            private final Long id;
            private final String currencyCode;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
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
        public static class UnitProductPeriodHistorySummary {

            private final Long id;
            private final String currencyCode;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
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
