package com.eformworks.signstage.backend.feature.ceremony.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 업로드는 multipart(title/documentRole/file)라 별도 Request DTO 없이 컨트롤러에서
 * {@code @RequestParam}으로 받는다(파일 업로드 표준 패턴).
 */
public final class TemplateDto {

    private TemplateDto() {
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class TemplateSummary {

            private final Long id;
            private final Long ceremonyId;
            private final String title;
            private final String documentRole;
            private final String originalFilename;
            private final String status;
            private final LocalDateTime createdAt;
        }
    }
}
