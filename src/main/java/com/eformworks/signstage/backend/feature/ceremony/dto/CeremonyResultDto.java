package com.eformworks.signstage.backend.feature.ceremony.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class CeremonyResultDto {

    private CeremonyResultDto() {
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class CeremonyResultSummary {

            private final Long id;
            private final Long ceremonyEventId;
            private final Long templateId;
            private final String resultType;
            private final String originalFilename;
            private final Long fileSize;
            private final String checksum;
            private final LocalDateTime createdAt;
        }
    }
}
