package com.eformworks.signstage.backend.feature.platformadmin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

            /**
             * OWNER로 지정할 기존 사용자의 로그인 아이디. 계정을 새로 만들지 않는다 — 이미 있는
             * 계정만 지정할 수 있다. {@code isDemo=true}면 이 값은 무시되고(생략 가능) 서버가
             * 자리표시자 계정을 자동으로 만든다 — {@link #isDemo} 참고. 그 외에는 필수다
             * (서비스 레이어에서 검증한다 — 조건부 필수라 {@code @NotBlank}를 붙이지 않았다).
             */
            private String ownerLoginId;

            /**
             * 데모 조직으로 생성할지 — signstage-docs
             * business/demo-account-exhibition-signer-preview-review.md 11.3절. true면
             * {@code ownerLoginId}를 무시하고 서버가 조직 코드로부터 결정적으로 로그인 아이디를
             * 만들어 자리표시자 OWNER 계정을 즉시 발급한다(아무도 로그인하지 않는다) — 관리자가
             * 매번 별도 계정을 미리 만들어 지정할 필요가 없다. 데모 조직에서는 플랫폼 관리자가
             * 이 계정 없이도(`findActiveMemberOrThrow` 우회로) 행사를 직접 관리할 수 있다.
             * 생략하면(null) false.
             */
            private Boolean isDemo;
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

        /**
         * 플랫폼 관리자의 파트너 정보 수정(2026-08-30 요청 — "플랫폼 관리자가 파트너 정보도
         * 수정할 수 있도록"). {@code OrganizationDto.Request.UpdateOrganization}(OWNER 전용)과
         * 같은 필드다 — code는 조직 식별자라 이 API로도 바꾸지 않는다.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateOrganizationInfo {

            @NotBlank
            private String organizationName;

            @NotBlank
            private String defaultLocale;
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
            private final boolean isDemo;

            /**
             * Lombok {@code @Getter}가 만들었을 getter는 {@code isDemo()}인데, Jackson은 boolean
             * getter의 "is" 접두어를 벗겨 JSON 키를 "demo"로 만들어버린다(프런트는 "isDemo" 키를
             * 기대한다). 그래서 이 getter만 직접 선언해 @JsonProperty로 키를 고정한다 — 클래스
             * 레벨 @Getter는 이미 이름이 같은 메서드가 있으면 따로 만들지 않으므로 중복 프로퍼티가
             * 생기지 않는다(2026-09-09 발견·수정).
             */
            @JsonProperty("isDemo")
            public boolean isDemo() {
                return isDemo;
            }
        }
    }
}
