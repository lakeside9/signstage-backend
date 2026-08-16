package com.eformworks.signstage.backend.feature.platformadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 플랫폼 관리자의 조직 생성 요청 승인/반려용 DTO. signstage-docs
 * business/organization-creation-approval-review.md를 따른다.
 */
public final class PlatformAdminOrganizationRequestDto {

    private PlatformAdminOrganizationRequestDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** 요청은 코드를 다루지 않는다(3.3절) — 승인 시점에 관리자가 정한다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Approve {

            @NotBlank
            @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$", message = "영문 소문자, 숫자, '-'만 사용할 수 있습니다.")
            private String code;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Reject {

            @NotBlank
            private String rejectionReason;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class RequestSummary {

            private final Long id;
            private final Long requesterId;
            private final String requesterLoginId;
            private final String requesterName;
            private final String organizationName;
            private final String note;
            private final String status;
            private final String rejectionReason;
            private final String reviewerLoginId;
            private final LocalDateTime reviewedAt;
            private final Long organizationId;
            private final LocalDateTime createdAt;
        }
    }
}
