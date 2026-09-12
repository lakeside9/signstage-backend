package com.eformworks.signstage.backend.feature.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FAQ(플랫폼 관리자 등록/수정) — signstage-docs business/partner-support-center-review.md 4장. */
public final class FaqDto {

    private FaqDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateFaq {

            @Size(max = 50)
            private String category;

            @NotBlank
            @Size(max = 500)
            private String question;

            @NotBlank
            private String answer;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateFaq {

            @Size(max = 50)
            private String category;

            @NotBlank
            @Size(max = 500)
            private String question;

            @NotBlank
            private String answer;

            @NotNull
            private Boolean active;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class FaqSummary {

            private final Long id;
            private final String category;
            private final String question;
            private final String answer;
            private final int displayOrder;
            private final boolean active;
            private final LocalDateTime createdAt;
        }
    }
}
