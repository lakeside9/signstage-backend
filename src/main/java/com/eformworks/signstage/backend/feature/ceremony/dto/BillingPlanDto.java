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
            private final List<Long> optionalFeatureIds;
            private final LocalDateTime createdAt;
        }
    }
}
