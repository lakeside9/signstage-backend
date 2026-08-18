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

            @NotNull
            private BigDecimal supplyPrice;

            @NotNull
            private BigDecimal salePrice;

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;
        }

        /**
         * {@code capacityType}은 상품의 종류를 규정하는 값이라 생성 후 불변이라
         * {@link CreateCapacityAddOn}과 달리 여기엔 없다 — 플랫폼 관리자 카탈로그 관리 화면 결정.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateCapacityAddOn {

            @NotNull
            @Min(1)
            private Integer unitAmount;

            @NotNull
            private BigDecimal supplyPrice;

            @NotNull
            private BigDecimal salePrice;

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
        public static class CapacityAddOnSummary {

            private final Long id;
            private final String capacityType;
            private final Integer unitAmount;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String discountType;
            private final BigDecimal discountValue;
            private final LocalDateTime createdAt;
        }
    }
}
