package com.eformworks.signstage.backend.feature.platformadmin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 현장지원 요청(관리자 견적) — 플랫폼 관리자 쪽 — signstage-docs
 * business/onsite-support-negotiation-and-billing-classification-review.md 3.2절.
 * 목록 조회는 {@code PlatformAdminCeremonyInquiryDto}와 같은 조직 横단 조회 패턴이다.
 */
public final class PlatformAdminOnsiteSupportRequestDto {

    private PlatformAdminOnsiteSupportRequestDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Quote {

            @NotNull
            @DecimalMin(value = "0", inclusive = true)
            private BigDecimal quotedAmount;

            @Size(max = 500)
            private String quotedNote;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class RequestSummary {

            private final Long id;
            private final Long organizationId;
            private final String organizationName;
            private final Long ceremonyId;
            private final String ceremonyTitle;
            private final String requesterLoginId;
            private final String requesterName;
            private final LocalDateTime requestedAt;
            private final String location;
            private final String requesterNote;
            private final String status;
            private final BigDecimal quotedAmount;
            private final String quotedNote;
            private final String quotedByLoginId;
            private final LocalDateTime quotedAt;
            private final LocalDateTime respondedAt;
            private final LocalDateTime createdAt;
        }
    }
}
