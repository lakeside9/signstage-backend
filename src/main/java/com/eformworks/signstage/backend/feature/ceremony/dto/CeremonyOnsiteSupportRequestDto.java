package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 현장지원 요청(관리자 견적) — 파트너 쪽 — signstage-docs
 * business/onsite-support-negotiation-and-billing-classification-review.md 3.2절.
 */
public final class CeremonyOnsiteSupportRequestDto {

    private CeremonyOnsiteSupportRequestDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateRequest {

            @NotNull
            private LocalDateTime requestedAt;

            @NotBlank
            @Size(max = 200)
            private String location;

            @Size(max = 500)
            private String requesterNote;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class RequestSummary {

            private final Long id;
            private final Long ceremonyId;
            private final LocalDateTime requestedAt;
            private final String location;
            private final String requesterNote;
            private final String status;
            private final BigDecimal quotedAmount;
            private final String quotedNote;
            private final LocalDateTime quotedAt;
            private final LocalDateTime respondedAt;
            private final LocalDateTime createdAt;
        }
    }
}
