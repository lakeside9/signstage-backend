package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class SignerPortalDto {

    private SignerPortalDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SubmitStroke {

            @NotNull
            private Long templateFieldId;

            @NotNull
            private Integer strokeSeq;

            @NotBlank
            private String rawData;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class PortalContext {

            private final Long eventId;
            private final String eventName;
            private final String eventStatus;
            private final Long signerId;
            private final String signerName;
            private final List<RequiredFieldStatus> requiredFields;
        }

        @Getter
        @AllArgsConstructor
        public static class RequiredFieldStatus {

            private final Long templateFieldId;
            private final Long templateId;
            private final String fieldName;
            private final Integer pageIndex;
            private final boolean hasStroke;
        }

        /**
         * 서명자 포털의 서명용(CONTRACT) 문서 배경 — legacy {@code SignerView.tsx}처럼 문서 전체를
         * 보여주고 그 위에 서명란을 오버레이로 겹쳐 그리기 위한 정보다. CONTRACT 매핑이 없으면
         * {@code null}(READY 전이 조건상 STARTED 이후엔 항상 있다).
         *
         * <p>{@code fields}는 이 서명자 본인 것만이 아니라 문서에 배치된 전체 서명란이다 — 다른
         * 서명자의 서명란도 흐릿하게 함께 보여주는 게 legacy의 UX다. 필드별 {@code signerId}는
         * {@link TemplateFieldDto.Response.TemplateFieldSummary}에 이미 포함돼 있어(프로젝터
         * 컨텍스트와 같은 관례) 프론트가 "내 서명란인지"를 자기 {@code signerId}와 비교해 판단한다.
         */
        @Getter
        @AllArgsConstructor
        public static class PortalContractDocument {

            private final Long templateId;
            private final String title;
            private final Integer pageCount;
            private final Float width;
            private final Float height;
            private final List<TemplateFieldDto.Response.TemplateFieldSummary> fields;
        }

        @Getter
        @AllArgsConstructor
        public static class StrokeSubmitted {

            private final Long id;
            private final Long templateFieldId;
            private final Integer strokeSeq;
            private final LocalDateTime createdAt;
        }
    }
}
