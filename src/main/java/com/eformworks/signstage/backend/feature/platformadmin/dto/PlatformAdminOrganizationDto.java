package com.eformworks.signstage.backend.feature.platformadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class PlatformAdminOrganizationDto {

    private PlatformAdminOrganizationDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateOrganization {

            @NotBlank
            private String organizationName;

            @NotBlank
            @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$", message = "영문 소문자, 숫자, '-'만 사용할 수 있습니다.")
            private String code;

            /** OWNER로 지정할 기존 사용자의 로그인 아이디. 계정을 새로 만들지 않는다 — 이미 있는 계정만 지정할 수 있다. */
            @NotBlank
            private String ownerLoginId;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateStatus {

            /** ACTIVE(재개) 또는 SUSPENDED(정지)만 허용한다. TRIAL은 과금 연동 시점에 다시 다룬다. */
            @NotBlank
            private String status;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class OrganizationSummary {

            private final Long id;
            private final String name;
            private final String code;
            private final String status;
            private final String defaultLocale;
            private final long activeMemberCount;
            private final LocalDateTime createdAt;
        }
    }
}
