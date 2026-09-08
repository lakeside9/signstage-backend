package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

            /** 생략하면(null) true — 프로젝터 화면 효과 옵션으로 등록된다. */
            private Boolean projectorEffect;

            /** 같은 값을 가진 다른 선택옵션과 한 CeremonyEvent에 동시 적용할 수 없다. 생략하면(null) 배타 관계 없음. */
            private String exclusivityGroup;

            /**
             * 이 묶음이 열어주는 이벤트 효과 목록 — {@code code='EVENT_EFFECT_BUNDLE'}일 때만
             * 의미가 있다(그 외 종류에서 값이 오면 무시하지 않고 거부한다). 생략하면(null)
             * 빈 묶음으로 시작한다(2026-09-08 결정).
             */
            private List<Long> effectDefinitionIds;
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

            @NotNull
            private Boolean projectorEffect;

            /** 같은 값을 가진 다른 선택옵션과 한 CeremonyEvent에 동시 적용할 수 없다. null이면 배타 관계 없음. */
            private String exclusivityGroup;

            /**
             * 이 묶음이 열어주는 이벤트 효과 목록을 통째로 교체한다(delete-all-then-recreate) —
             * {@code code='EVENT_EFFECT_BUNDLE'}일 때만 의미가 있다. 생략하면(null) 기존 구성을
             * 그대로 둔다. 빈 배열을 명시적으로 보내면 전부 해제한다.
             */
            private List<Long> effectDefinitionIds;
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
            private final String currencyCode;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String discountType;
            private final BigDecimal discountValue;
            private final String taxCode;
            private final Boolean active;
            private final Boolean projectorEffect;
            private final String exclusivityGroup;
            /** 이 옵션을 승인받아 쓰는 구매 건수 — 카탈로그 관리 화면의 "사용 중" 경고용. */
            private final Long usageCount;
            /** 이 묶음이 여는 이벤트 효과 id 목록. {@code code='EVENT_EFFECT_BUNDLE'}가 아니면 항상 빈 배열이다. */
            private final List<Long> effectDefinitionIds;
            private final LocalDateTime createdAt;
        }

        /** 선택옵션 값/사용여부 변경 이력 한 행 — 그 변경 시점의 전체 상태 스냅샷이다. */
        @Getter
        @AllArgsConstructor
        public static class OptionalFeatureHistorySummary {

            private final Long id;
            private final String code;
            private final String name;
            private final String currencyCode;
            private final BigDecimal supplyPrice;
            private final BigDecimal salePrice;
            private final String discountType;
            private final BigDecimal discountValue;
            private final String taxCode;
            private final Boolean active;
            private final Boolean projectorEffect;
            private final String exclusivityGroup;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }
    }
}
