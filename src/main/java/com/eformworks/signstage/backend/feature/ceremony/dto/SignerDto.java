package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class SignerDto {

    private SignerDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateSigner {

            @NotBlank
            private String name;

            private String position;

            private String affiliation;

            private String roleCode;
        }

        /** accessKey는 여기서 바꾸지 않는다(포털 접속에 쓰이는 값이라 고정). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateSigner {

            @NotBlank
            private String name;

            private String position;

            private String affiliation;

            private String roleCode;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class SignerSummary {

            private final Long id;
            private final Long ceremonyId;
            private final String name;
            private final String position;
            private final String affiliation;
            private final String roleCode;
            private final String accessKey;
            /** 시작/종료된 하위 행사에 배정돼 수정이 막힌 서명자인지 — true면 화면에서 수정 버튼을 비활성화한다. */
            private final boolean locked;
            private final LocalDateTime createdAt;
        }
    }
}
