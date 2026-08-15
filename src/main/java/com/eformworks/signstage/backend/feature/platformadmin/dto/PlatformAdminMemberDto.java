package com.eformworks.signstage.backend.feature.platformadmin.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 플랫폼 관리자의 조직 멤버 강제 조정(signstage-docs
 * business/platform-admin-member-management.md 4.2절 "조직 멤버십 강제 조정"). 형태는
 * {@link com.eformworks.signstage.backend.feature.organization.dto.MemberDto}와 같지만,
 * 다른 platformadmin DTO들과 같은 이유로 별도 파일을 둔다(PlatformAdminOrganizationDto가
 * OrganizationDto와 별도인 것과 동일).
 */
public final class PlatformAdminMemberDto {

    private PlatformAdminMemberDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ChangeRole {

            @NotBlank
            private String role;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class MemberSummary {

            private final Long id;
            private final Long organizationId;
            private final Long userId;
            private final String loginId;
            private final String name;
            private final String email;
            private final String role;
            private final String status;
            private final LocalDateTime joinedAt;
        }
    }
}
