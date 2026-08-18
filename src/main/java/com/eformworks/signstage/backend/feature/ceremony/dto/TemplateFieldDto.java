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

public final class TemplateFieldDto {

    private TemplateFieldDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateTemplateField {

            @NotBlank
            private String fieldKey;

            @NotNull
            private Integer pageIndex;

            @NotNull
            private Integer fieldIndex;

            @NotBlank
            private String fieldName;

            private String roleCode;

            private Integer signOrder;

            private Boolean isRequired;

            /** 아직 서명자를 배정하지 않은 필드는 생략할 수 있다. */
            private Long signerId;

            @NotNull
            private BigDecimal xRatio;

            @NotNull
            private BigDecimal yRatio;

            @NotNull
            private BigDecimal widthRatio;

            @NotNull
            private BigDecimal heightRatio;
        }

        /**
         * 서명란 배치 화면의 "저장" — 항상 현재 전체 필드 배열을 통째로 보낸다(diff 없음).
         * 서버도 기존 필드를 전부 지우고 이 배열로 다시 채운다(legacy TemplateService.setFields와
         * 같은 규약).
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SetFields {

            @NotNull
            private List<CreateTemplateField> fields;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class TemplateFieldSummary {

            private final Long id;
            private final Long templateId;
            private final Long signerId;
            private final String fieldKey;
            private final Integer pageIndex;
            private final Integer fieldIndex;
            private final String fieldName;
            private final String roleCode;
            private final Integer signOrder;
            private final Boolean isRequired;
            private final BigDecimal xRatio;
            private final BigDecimal yRatio;
            private final BigDecimal widthRatio;
            private final BigDecimal heightRatio;
            private final LocalDateTime createdAt;
        }
    }
}
