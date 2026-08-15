package com.eformworks.signstage.backend.feature.platformadmin.dto;

import jakarta.validation.constraints.NotBlank;
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
