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
            private Integer maxMainEvents;

            /** 이 플랜에 기본으로 포함할 선택옵션 id 목록(생략하면 빈 목록). */
            private List<Long> optionalFeatureIds;
        }

        /**
         * 선택옵션 구성({@code optionalFeatureIds})은 생성 시점에만 정해지고 이후 불변이라
         * {@link CreatePlan}과 달리 여기엔 없다 — 플랫폼 관리자 카탈로그 관리 화면 결정.
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
            private Integer maxMainEvents;

            /** 사용여부. false면 새 행사 생성/플랜 변경 대상에서 제외된다. */
            @NotNull
            private Boolean active;
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
            private final Integer maxMainEvents;
            private final Boolean active;
            private final List<Long> optionalFeatureIds;
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
            private final Integer maxMainEvents;
            private final Boolean active;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }
    }
}
