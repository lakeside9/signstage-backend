package com.eformworks.signstage.backend.feature.platformadmin.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class PlatformAdminAuditLogDto {

    private PlatformAdminAuditLogDto() {
    }

    public static final class Response {
        private Response() {
        }

        /**
         * adminLoginId/targetLoginId/organizationName은 각각 admin_user_id/target_user_id/
         * organization_id를 조회 시점에 조인해 채운 표시용 값이다(원본 로그는 id만 가진다).
         */
        @Getter
        @AllArgsConstructor
        public static class AuditLogEntry {

            private final Long id;
            private final Long adminUserId;
            private final String adminLoginId;
            private final String action;
            private final Long targetUserId;
            private final String targetLoginId;
            private final Long organizationId;
            private final String organizationName;
            private final String detail;
            private final String requestPath;
            private final LocalDateTime createdAt;
        }
    }
}
