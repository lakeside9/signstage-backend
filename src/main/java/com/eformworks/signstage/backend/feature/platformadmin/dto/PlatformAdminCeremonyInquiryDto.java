package com.eformworks.signstage.backend.feature.platformadmin.dto;

import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyInquiryDto;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 플랫폼 관리자의 행사별 1:1 문의 조회/답변/종료 API용 — signstage-docs
 * business/partner-support-center-review.md 5.3절. 목록 조회는
 * {@code PlatformAdminCeremonyPurchaseDto}와 같은 조직 横단 조회 패턴이다(organizationId/
 * ceremonyId/status/requesterKeyword 전부 선택 필터, 소유권 검사 아님).
 */
public final class PlatformAdminCeremonyInquiryDto {

    private PlatformAdminCeremonyInquiryDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Reply {

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
            private final Long organizationId;
            private final String organizationName;
            private final Long ceremonyId;
            private final String ceremonyTitle;
            private final String requesterLoginId;
            private final String requesterName;
            private final String title;
            private final String status;
            private final LocalDateTime lastMessageAt;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class InquiryDetail {

            private final Long id;
            private final Long organizationId;
            private final String organizationName;
            private final Long ceremonyId;
            private final String ceremonyTitle;
            private final String requesterLoginId;
            private final String requesterName;
            private final String title;
            private final String status;
            private final LocalDateTime lastMessageAt;
            private final LocalDateTime createdAt;
            private final List<CeremonyInquiryDto.Response.MessageSummary> messages;
        }
    }
}
