package com.eformworks.signstage.backend.feature.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 체험형 데모 프로필 DTO — signstage-docs
 * business/demo-account-exhibition-signer-preview-review.md 13장 결정(2026-09-10). 공개 응답
 * (`Config`/`PublicSummary`)은 legacy 데모 셸(`demo-signstage-frontend`, 별도 저장소, 수정하지
 * 않고 그대로 재사용)이 이미 소비하는 필드 이름·모양을 그대로 맞췄다 — 그래야 그 저장소를
 * 전혀 건드리지 않고 이 백엔드만 바라보게 할 수 있다. 관리자용 DTO(`Upsert`/`DemoEventOption`
 * 등)는 legacy의 `hasRole("ADMIN")` 방식을 따르지 않고 이 프로젝트 관례(동적 RBAC,
 * `platform-admin` 콘솔)에 맞춰 새로 설계했다 — legacy 관리 화면(`DemoSettings.tsx`)은
 * 재사용하지 않기 때문이다.
 */
public final class DemoConfigDto {

    private DemoConfigDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** slug로 upsert — 이미 있으면 갱신, 없으면 새로 만든다(legacy와 같은 방식). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Upsert {

            @NotBlank
            @Pattern(regexp = "^[a-z0-9-]{1,50}$", message = "식별자는 영문 소문자, 숫자, 하이픈(-)만 사용할 수 있습니다.")
            private String slug;

            @NotBlank
            private String eventAccessKey;

            @NotEmpty
            private List<String> signerAccessKeys;

            /** 전시 화면 도구모음 미리 지정용(선택) — 비워두면 ProjectorView 기본값을 쓴다. */
            @Min(1)
            @Max(3)
            private Integer pages;

            @Min(1)
            private Integer startPage;

            @Pattern(regexp = "^(spaced|joined)$", message = "spacing은 spaced 또는 joined만 가능합니다.")
            private String spacing;

            private Boolean enabled;
        }
    }

    public static final class Response {
        private Response() {
        }

        /**
         * {@code GET /api/demo/config?slug=} 응답 — legacy `DemoConfigDto.Response.Config`와
         * 같은 필드. {@code projectorUrl}/{@code signerUrls}는 origin 없는 상대 경로다(이
         * 프로젝트의 실제 라우트 {@code /projector/:eventAccessKey},
         * {@code /portal/:eventAccessKey/:signerAccessKey} 모양으로 만든다) — 데모 셸이 자신이
         * 아는 origin을 앞에 붙여 iframe src로 쓴다.
         */
        @Getter
        @AllArgsConstructor
        public static class Config {

            private final String slug;
            private final String eventAccessKey;
            private final List<String> signerAccessKeys;
            private final Integer pages;
            private final Integer startPage;
            private final String spacing;
            private final boolean enabled;
            private final String projectorUrl;
            private final List<String> signerUrls;
            private final LocalDateTime updatedAt;
        }

        /**
         * {@code GET /api/demo/configs} 응답 — 데모 셸의 프로필 선택 목록 화면용. legacy
         * `DemoConfigDto.Response.PublicSummary`와 같은 필드.
         */
        @Getter
        @AllArgsConstructor
        public static class PublicSummary {

            private final String slug;
            private final boolean enabled;
            private final String eventName;
            private final String ceremonyTitle;
            private final int signerCount;
        }

        /** 관리자 콘솔 프로필 목록/생성/수정 응답 — {@link Config}를 그대로 재사용한다. */

        /** 관리자 콘솔이 프로필 생성 시 고를 수 있는 데모 행사 하나 + 그 행사의 서명자 후보. */
        @Getter
        @AllArgsConstructor
        public static class DemoEventOption {

            private final Long ceremonyEventId;
            private final String eventAccessKey;
            private final String eventName;
            private final String ceremonyTitle;
            private final List<SignerOption> signers;
        }

        @Getter
        @AllArgsConstructor
        public static class SignerOption {

            private final Long signerId;
            private final String signerAccessKey;
            private final String name;
            private final String affiliation;
            private final String position;
        }
    }
}
