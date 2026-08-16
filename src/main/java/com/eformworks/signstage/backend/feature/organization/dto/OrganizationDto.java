package com.eformworks.signstage.backend.feature.organization.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 조직 조회/수정 응답·요청 DTO. 생성 요청 DTO는 {@code OrganizationCreationRequestDto}(일반 사용자),
 * {@code PlatformAdminOrganizationRequestDto}(관리자 승인/반려)를 따로 쓴다 —
 * signstage-docs business/organization-creation-approval-review.md.
 */
public final class OrganizationDto {

    private OrganizationDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** OWNER만 호출할 수 있다({@code OrganizationService#updateOrganization}). code는 여기서 바꾸지 않는다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateOrganization {

            @NotBlank
            private String name;

            @NotBlank
            private String defaultLocale;
        }
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
            /** 호출한 사용자가 이 조직에서 가진 역할(OWNER/ADMIN/OPERATOR/VIEWER). 조직 설정 수정 가능 여부를 프런트에서 판단하는 데 쓴다. */
            private final String myRole;
        }
    }
}
