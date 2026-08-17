package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class CeremonyEventDto {

    private CeremonyEventDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateCeremonyEvent {

            @NotBlank
            private String name;

            /** TEST / MAIN. */
            @NotBlank
            private String eventType;

            private String venue;

            private LocalDateTime scheduledStartAt;

            private LocalDateTime scheduledEndAt;

            private String description;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateOptionalFeatures {

            /** 이 이벤트에 켤 선택옵션 id 목록. Ceremony가 구매한 옵션의 부분집합이어야 한다. */
            @NotNull
            private List<Long> optionalFeatureIds;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class MapTemplate {

            @NotNull
            private Long templateId;

            /** CONTRACT / EXHIBITION. */
            @NotBlank
            private String documentRole;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class CeremonyEventSummary {

            private final Long id;
            private final Long ceremonyId;
            private final String name;
            private final String eventType;
            private final String status;
            private final String venue;
            private final LocalDateTime scheduledStartAt;
            private final LocalDateTime scheduledEndAt;
            private final LocalDateTime actualStartAt;
            private final LocalDateTime actualEndAt;
            private final String accessKey;
            private final String description;
            private final List<Long> optionalFeatureIds;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class CeremonyTemplateSummary {

            private final Long id;
            private final Long ceremonyEventId;
            private final Long templateId;
            private final String documentRole;
            private final LocalDateTime createdAt;
        }
    }
}
