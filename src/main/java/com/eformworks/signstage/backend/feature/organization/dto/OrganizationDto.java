package com.eformworks.signstage.backend.feature.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class OrganizationDto {

    private OrganizationDto() {
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
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class Organization {

            private final Long id;
            private final String name;
            private final String code;
            private final String status;
            private final String defaultLocale;
            private final LocalDateTime createdAt;
        }
    }
}
