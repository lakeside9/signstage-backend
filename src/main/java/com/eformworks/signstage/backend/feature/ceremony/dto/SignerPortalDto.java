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
