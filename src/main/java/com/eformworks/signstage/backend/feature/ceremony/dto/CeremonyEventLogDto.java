package com.eformworks.signstage.backend.feature.ceremony.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class CeremonyEventLogDto {

    private CeremonyEventLogDto() {
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class CeremonyEventLogSummary {

            private final Long id;
            private final Long ceremonyEventId;
            private final String actorType;
            private final Long actorId;
            private final String eventAction;
            private final Long targetSignerId;
            private final String message;
            private final LocalDateTime createdAt;
        }
    }
}
