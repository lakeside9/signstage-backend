package com.eformworks.signstage.backend.feature.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 공지사항(플랫폼 관리자 등록/수정) — signstage-docs business/partner-support-center-review.md 3장. */
public final class AnnouncementDto {

    private AnnouncementDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateAnnouncement {

            @NotBlank
            @Size(max = 200)
            private String title;

            @NotBlank
            private String content;

            /** 생략하면(null) false. */
            private Boolean pinned;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateAnnouncement {

            @NotBlank
            @Size(max = 200)
            private String title;

            @NotBlank
            private String content;

            @NotNull
            private Boolean pinned;

            @NotNull
            private Boolean active;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class AnnouncementSummary {

            private final Long id;
            private final String title;
            private final String content;
            private final boolean pinned;
            private final boolean active;
            private final LocalDateTime createdAt;
        }
    }
}
