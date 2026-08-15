package com.eformworks.signstage.backend.feature.platformadmin.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 특정 회원의 로그인 이력 조회 응답. signstage-docs business/login-security.md 6장
 * "조회 권한은 PLATFORM_OPS 이상"을 그대로 따른다(PLATFORM_SUPPORT는 조회 불가 — 다른
 * platform-admin 조회 API 대부분이 SUPPORT+인 것과 다른 예외다).
 */
public final class PlatformAdminLoginHistoryDto {

    private PlatformAdminLoginHistoryDto() {
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class LoginHistoryEntry {

            private final Long id;
            private final String loginIdInput;
            private final String status;
            private final String ipAddress;
            private final String userAgent;
            private final LocalDateTime createdAt;
        }
    }
}
