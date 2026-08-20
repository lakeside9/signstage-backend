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

        /**
         * {@code code}는 옵션의 종류를 규정하는 값이라 생성 후 불변이라 {@link CreateOptionalFeature}와
         * 달리 여기엔 없다 — 플랫폼 관리자 카탈로그 관리 화면 결정.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateOptionalFeature {

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

            /** 사용여부. false면 새 추가구매 대상에서 제외된다. */
            @NotNull
            private Boolean active;
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
            private final Boolean active;
            /** 이 옵션을 승인받아 쓰는 구매 건수 — 카탈로그 관리 화면의 "사용 중" 경고용. */
            private final Long usageCount;
            private final LocalDateTime createdAt;
        }

        /** 선택옵션 값/사용여부 변경 이력 한 행 — 그 변경 시점의 전체 상태 스냅샷이다. */
        @Getter
        @AllArgsConstructor
        public static class OptionalFeatureHistorySummary {

            private final Long id;
            private final String code;
            private final String name;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String discountType;
            private final BigDecimal discountValue;
            private final Boolean active;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }
    }
}
