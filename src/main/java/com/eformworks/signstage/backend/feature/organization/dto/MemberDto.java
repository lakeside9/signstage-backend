package com.eformworks.signstage.backend.feature.organization.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class MemberDto {

    private MemberDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class AddMember {

            @NotBlank
            private String loginId;

            @NotBlank
            private String role;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ChangeRole {

            @NotBlank
            private String role;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class MemberSummary {

            private final Long id;
            private final Long organizationId;
            private final Long userId;
            private final String loginId;
            private final String name;
            private final String email;
            private final String role;
            private final String status;
            private final LocalDateTime joinedAt;
        }
    }
}
