package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 행사 이벤트 효과 카탈로그 정의(플랫폼 관리자 등록/수정) — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-CATALOG-02.
 */
public final class CeremonyEffectDefinitionDto {

    private CeremonyEffectDefinitionDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateCeremonyEffectDefinition {

            @NotBlank
            @Size(max = 50)
            @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "대문자/숫자/밑줄만 사용하고 대문자로 시작해야 합니다.")
            private String code;

            @NotBlank
            private String targetType;

            @NotBlank
            private String triggerType;

            @NotNull
            private Long requiredOptionalFeatureId;

            @NotBlank
            @Size(max = 100)
            private String displayName;

            @Size(max = 500)
            private String description;

            @NotBlank
            @Size(max = 100)
            private String rendererKey;

            /** 생략하면(null) false — 전체완료 효과라도 기본은 자동 실행 전용이다. */
            private Boolean manuallyTriggerable;

            /** JSON object만 허용한다(배열/스칼라는 역직렬화 단계에서 이미 거부된다). 생략 가능. */
            private Map<String, Object> configJson;
        }

        /**
         * {@code code}/{@code targetType}/{@code triggerType}/{@code rendererKey}/
         * {@code requiredOptionalFeatureId}는 등록 후 불변이라 {@link CreateCeremonyEffectDefinition}과
         * 달리 여기엔 없다.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateCeremonyEffectDefinition {

            @NotBlank
            @Size(max = 100)
            private String displayName;

            @Size(max = 500)
            private String description;

            @NotNull
            private Boolean enabled;

            @NotNull
            private Boolean userVisible;

            @NotNull
            private Boolean manuallyTriggerable;

            private Map<String, Object> configJson;
        }

        /**
         * 같은 분류(target, trigger) 안에서의 위/아래 이동 — 목록 화면이 그 그룹 전체를 원하는
         * 순서로 다시 나열해 id만 통째로 보낸다. 실제 표시 순서 값(10, 20, 30 ...)은 서버가
         * 이 나열 순서를 기준으로 다시 매긴다(재정규화) — 클라이언트가 보낸 정수 값 자체는
         * 신뢰하지 않는다.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ReorderCeremonyEffectDefinitions {

            @NotBlank
            private String targetType;

            @NotBlank
            private String triggerType;

            @NotEmpty
            private List<Long> orderedIds;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class CeremonyEffectDefinitionSummary {

            private final Long id;
            private final String code;
            private final String targetType;
            private final String triggerType;
            private final Long requiredOptionalFeatureId;
            private final String displayName;
            private final String description;
            private final String rendererKey;
            private final Boolean enabled;
            private final Boolean userVisible;
            private final Boolean manuallyTriggerable;
            private final Integer displayOrder;
            private final Map<String, Object> configJson;
            private final LocalDateTime createdAt;
        }
    }
}
