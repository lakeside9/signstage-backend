package com.eformworks.signstage.backend.feature.platformadmin.dto;

import jakarta.validation.constraints.Email;
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

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateUser {

            @NotBlank
            private String loginId;

            @NotBlank
            private String name;

            @NotBlank
            @Email
            private String email;

            private String phone;

            private String locale;
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
            /** 연속 로그인 실패로 현재 잠긴 상태인지(잠금 즉시 해제 버튼 노출 판단용). */
            private final boolean locked;
            /** 다음 로그인 시 비밀번호 변경이 강제되는 상태인지(강제 재설정 중복 요청 방지용). */
            private final boolean passwordResetRequired;
            private final LocalDateTime createdAt;
        }

        /**
         * 회원 생성 응답 전용. {@code temporaryPassword}는 이 응답에만 담기고 어디에도
         * 저장되지 않는다 — 관리자가 이 화면을 벗어나면 다시 조회할 수 없다.
         */
        @Getter
        @AllArgsConstructor
        public static class CreatedUser {

            private final UserSummary user;
            private final String temporaryPassword;
        }
    }
}
