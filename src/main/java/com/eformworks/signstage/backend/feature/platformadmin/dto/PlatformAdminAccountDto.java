package com.eformworks.signstage.backend.feature.platformadmin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 플랫폼 관리자 계정 관리(PLATFORM_SUPER 전용, signstage-docs
 * business/user-organization-design.md 7.2절) 요청 DTO. 응답은 회원 생성과 형태가 같아
 * {@link PlatformAdminUserDto.Response#UserSummary}/{@code CreatedUser}를 그대로 재사용한다.
 */
public final class PlatformAdminAccountDto {

    private PlatformAdminAccountDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateAccount {

            @NotBlank
            private String loginId;

            @NotBlank
            private String name;

            @NotBlank
            @Email
            private String email;

            private String phone;

            private String locale;

            /** PLATFORM_SUPPORT / PLATFORM_OPS / PLATFORM_SUPER 중 하나. */
            @NotBlank
            private String platformRole;
        }
    }
}
