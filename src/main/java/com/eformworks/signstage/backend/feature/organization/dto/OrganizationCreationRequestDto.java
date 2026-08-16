package com.eformworks.signstage.backend.feature.organization.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 일반 사용자의 조직 생성 요청 제출/조회용 DTO. signstage-docs
 * business/organization-creation-approval-review.md 3.2절을 따른다.
 */
public final class OrganizationCreationRequestDto {

    private OrganizationCreationRequestDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Create {

            @NotBlank
            private String organizationName;

            /** 부가설명(선택). 심사 근거를 요구하는 "사유"가 아니다. */
            private String note;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class RequestSummary {

            private final Long id;
            private final String organizationName;
            private final String note;
            private final String status;
            private final String rejectionReason;
            private final LocalDateTime reviewedAt;
            private final Long organizationId;
            private final LocalDateTime createdAt;
        }
    }
}
