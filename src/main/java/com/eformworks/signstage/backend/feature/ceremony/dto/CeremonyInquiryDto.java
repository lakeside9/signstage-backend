package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 행사별 1:1 문의(파트너 쪽) — signstage-docs business/partner-support-center-review.md 5장. */
public final class CeremonyInquiryDto {

    private CeremonyInquiryDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateInquiry {

            @NotBlank
            @Size(max = 200)
            private String title;

            @NotBlank
            private String content;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class AddMessage {

            @NotBlank
            private String content;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class InquirySummary {

            private final Long id;
            private final Long ceremonyId;
            private final String title;
            private final String status;
            private final LocalDateTime lastMessageAt;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class InquiryDetail {

            private final Long id;
            private final Long ceremonyId;
            private final String title;
            private final String status;
            private final LocalDateTime lastMessageAt;
            private final LocalDateTime createdAt;
            private final List<MessageSummary> messages;
        }

        @Getter
        @AllArgsConstructor
        public static class MessageSummary {

            private final Long id;
            private final String senderType;
            private final String content;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }
    }
}
