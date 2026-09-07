package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 행사 이벤트 하나가 (target, trigger) 분류별로 고른 효과 프리셋 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-SETTING. {@link Request.EffectSelection}은
 * {@code CeremonyEventDto.Request.CreateCeremonyEvent}/{@code UpdateCeremonyEvent}에도 그대로
 * 끼워 넣어 쓴다({@code DisplayOrderRequest}처럼 여러 컨트롤러가 공유하는 DTO).
 */
public final class CeremonyEventEffectSettingDto {

    private CeremonyEventEffectSettingDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class EffectSelection {

            @NotBlank
            private String targetType;

            @NotBlank
            private String triggerType;

            /** 이 분류에서 선택할 효과 정의 id. null이면 이 분류를 해제(NONE)한다. */
            private Long effectId;
        }

        /** 조직 스코프 GET/PUT 전용 API가 받는 요청 — 전체 교체(선택 안 한 분류는 전부 해제). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateEffectSelections {

            @NotNull
            @Valid
            private List<EffectSelection> selections;
        }
    }

    public static final class Response {

        /** 조직 스코프 조회와 공개 프로젝터 snapshot이 같은 모양을 쓴다(PRE-04 계약 — 내부 id/config 등은 원래도 없다). */
        @Getter
        @AllArgsConstructor
        public static class EffectSettingSummary {

            private final String targetType;
            private final String triggerType;
            private final String effectCode;
            private final String rendererKey;
            private final String displayName;
            private final Boolean runtimeEnabled;
            private final Boolean manuallyTriggerable;
        }
    }
}
