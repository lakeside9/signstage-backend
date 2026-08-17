package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class OptionalFeatureDto {

    private OptionalFeatureDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateOptionalFeature {

            @NotBlank
            private String code;

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
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class OptionalFeatureSummary {

            private final Long id;
            private final String code;
            private final String name;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String discountType;
            private final BigDecimal discountValue;
            private final LocalDateTime createdAt;
        }
    }
}
