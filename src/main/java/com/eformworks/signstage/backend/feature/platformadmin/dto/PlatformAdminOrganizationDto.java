package com.eformworks.signstage.backend.feature.platformadmin.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class PlatformAdminOrganizationDto {

    private PlatformAdminOrganizationDto() {
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
