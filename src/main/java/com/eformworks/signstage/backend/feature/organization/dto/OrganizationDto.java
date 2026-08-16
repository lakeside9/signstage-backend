package com.eformworks.signstage.backend.feature.organization.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 조직 조회 응답 DTO. 생성 요청 DTO는 {@code OrganizationCreationRequestDto}(일반 사용자),
 * {@code PlatformAdminOrganizationRequestDto}(관리자 승인/반려)를 따로 쓴다 —
 * signstage-docs business/organization-creation-approval-review.md.
 */
public final class OrganizationDto {

    private OrganizationDto() {
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class Organization {

            private final Long id;
            private final String name;
            private final String code;
            private final String status;
            private final String defaultLocale;
            private final LocalDateTime createdAt;
        }
    }
}
