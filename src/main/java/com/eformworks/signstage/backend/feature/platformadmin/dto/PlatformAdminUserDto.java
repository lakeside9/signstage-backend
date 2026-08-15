package com.eformworks.signstage.backend.feature.platformadmin.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class PlatformAdminUserDto {

    private PlatformAdminUserDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateStatus {

            /**
             * ACTIVE(승인) 또는 DISABLED(거절/비활성화)만 허용한다. PENDING은 가입 직후 기본값이고
             * WITHDRAWN은 회원 탈퇴 전용 상태라 이 API로 지정하지 않는다.
             */
            @NotBlank
            private String status;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class UserSummary {

            private final Long id;
            private final String loginId;
            private final String name;
            private final String email;
            private final String phone;
            private final String locale;
            private final String status;
            private final String platformRole;
            private final LocalDateTime createdAt;
        }
    }
}
