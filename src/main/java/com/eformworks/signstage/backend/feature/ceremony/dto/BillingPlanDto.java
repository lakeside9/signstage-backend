package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class BillingPlanDto {

    private BillingPlanDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreatePlan {

            @NotBlank
            private String name;

            @NotNull
            private BigDecimal supplyPrice;

            @NotNull
            private BigDecimal salePrice;

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;

            @NotNull
            @Min(0)
            private Integer maxSigners;

            @NotNull
            @Min(0)
            private Integer maxTemplates;

            @NotNull
            @Min(0)
            private Integer maxTestEvents;

            @NotNull
            @Min(0)
            private Integer maxRehearsalEvents;

            @NotNull
            @Min(0)
            private Integer maxMainEvents;

            /** 이 플랜에 기본으로 포함할 선택옵션 id 목록(생략하면 빈 목록). */
            private List<Long> optionalFeatureIds;

            /**
             * 이 플랜에서 구매 가능하게 열어줄 용량 추가구매 상품 id 목록(생략하면 빈 목록) — 안 A,
             * 무료 포함이 아니라 "구매 후보로 고를 수 있는" 큐레이션이다(signstage-docs
             * business/optional-feature-display-scope-and-plan-capacity-addon-review.md 4.1/5장).
             */
            private List<Long> capacityAddOnIds;
        }

        /**
         * 선택옵션 구성({@code optionalFeatureIds})·구매 가능 용량 추가구매 상품 구성
         * ({@code capacityAddOnIds})도 여기서 통째로 교체할 수 있다(9장 후속 결정 — 처음엔
         * 생성 후 불변이었으나 뺄 방법이 없어 문제였다). 생략하면 빈 목록으로 취급한다
         * ({@link CreatePlan}과 같은 규약). 이미 확정/진행 중인 행사는
         * {@code CeremonyPlanHistoryOptionalFeature}/{@code CeremonyPlanHistoryCapacityAddOn}
         * 스냅샷으로 보호되어 이 수정에 영향받지 않는다.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdatePlan {

            @NotBlank
            private String name;

            @NotNull
            private BigDecimal supplyPrice;

            @NotNull
            private BigDecimal salePrice;

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;

            @NotNull
            @Min(0)
            private Integer maxSigners;

            @NotNull
            @Min(0)
            private Integer maxTemplates;

            @NotNull
            @Min(0)
            private Integer maxTestEvents;

            @NotNull
            @Min(0)
            private Integer maxRehearsalEvents;

            @NotNull
            @Min(0)
            private Integer maxMainEvents;

            /** 사용여부. false면 새 행사 생성/플랜 변경 대상에서 제외된다. */
            @NotNull
            private Boolean active;

            /** 이 플랜에 기본으로 포함할 선택옵션 id 목록(생략하면 빈 목록 — 전부 뺀다는 뜻). */
            private List<Long> optionalFeatureIds;

            /** 이 플랜에서 구매 가능하게 열어줄 용량 추가구매 상품 id 목록(생략하면 빈 목록 — 전부 뺀다는 뜻). */
            private List<Long> capacityAddOnIds;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class BillingPlanSummary {

            private final Long id;
            private final String name;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String discountType;
            private final BigDecimal discountValue;
            private final Integer maxSigners;
            private final Integer maxTemplates;
            private final Integer maxTestEvents;
            private final Integer maxRehearsalEvents;
            private final Integer maxMainEvents;
            private final Boolean active;
            private final List<Long> optionalFeatureIds;
            /** 이 플랜에서 구매 가능한 용량 추가구매 상품 id 목록(안 A 큐레이션). */
            private final List<Long> capacityAddOnIds;
            /** 이 플랜을 쓰는 행사(Ceremony) 수 — 카탈로그 관리 화면의 "사용 중" 경고용. */
            private final Long usageCount;
            private final LocalDateTime createdAt;
        }

        /** 플랜 값/사용여부 변경 이력 한 행 — 그 변경 시점의 전체 상태 스냅샷이다. */
        @Getter
        @AllArgsConstructor
        public static class BillingPlanHistorySummary {

            private final Long id;
            private final String name;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String discountType;
            private final BigDecimal discountValue;
            private final Integer maxSigners;
            private final Integer maxTemplates;
            private final Integer maxTestEvents;
            private final Integer maxRehearsalEvents;
            private final Integer maxMainEvents;
            private final Boolean active;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }
    }
}
