package com.eformworks.signstage.backend.feature.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class IdentityDto {

    private IdentityDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Login {

            @NotBlank
            private String loginId;

            @NotBlank
            private String password;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ForcePasswordChange {

            @NotBlank
            private String passwordResetToken;

            @NotBlank
            private String currentPassword;

            @NotBlank
            private String newPassword;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateMe {

            @NotBlank
            private String name;

            @NotBlank
            @Email
            private String email;

            private String phone;

            private String languageCode;

            private String locale;

            private String timeZoneId;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ChangeMyPassword {

            @NotBlank
            private String currentPassword;

            @NotBlank
            private String newPassword;
        }

        /**
         * loginId는 이 요청에서 받지 않는다 — 이메일을 그대로 loginId로 쓴다
         * (signstage-docs business/user-organization-design.md 5.1절, 2026-08-16 결정).
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Signup {

            @NotBlank
            private String password;

            @NotBlank
            private String name;

            @NotBlank
            @Email
            private String email;

            private String phone;

            private String languageCode;

            private String locale;

            private String timeZoneId;
        }
    }

    public static final class Response {
        private Response() {
        }

        /**
         * 로그인 응답은 두 형태 중 하나다.
         * - 비밀번호 변경이 필요 없으면: accessToken/platformRole 등이 채워지고 passwordChangeRequired=false
         * - 비밀번호 변경이 필요하면: passwordResetToken만 채워지고 passwordChangeRequired=true,
         *   나머지 필드는 비워둔다(signstage-docs business/login-security.md 5장 흐름 참고)
         */
        @Getter
        @AllArgsConstructor
        public static class Login {

            private final boolean passwordChangeRequired;
            private final String passwordResetToken;
            private final String tokenType;
            private final String accessToken;
            private final PlatformAdminInfo platformAdmin;

            public static Login passwordChangeRequired(String passwordResetToken) {
                return new Login(true, passwordResetToken, null, null, null);
            }

            public static Login success(String accessToken, PlatformAdminInfo platformAdmin) {
                return new Login(false, null, "Bearer", accessToken, platformAdmin);
            }
        }

        @Getter
        @AllArgsConstructor
        public static class PlatformAdminInfo {

            private final Long id;
            private final String loginId;
            private final String name;
            private final String platformRole;
        }

        @Getter
        @AllArgsConstructor
        public static class Me {

            private final Long id;
            private final String loginId;
            private final String name;
            private final String email;
            private final String phone;
            private final String languageCode;
            private final String locale;
            private final String timeZoneId;
            private final String platformRole;
        }

        @Getter
        @AllArgsConstructor
        public static class Signup {

            private final Long id;
            private final String loginId;
            private final String status;
        }
    }
}
