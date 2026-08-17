package com.eformworks.signstage.backend.feature.ceremony.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class DocumentVerificationDto {

    private DocumentVerificationDto() {
    }

    public static final class Response {

        private Response() {
        }

        /**
         * {@code verified=false}면 나머지 필드는 전부 {@code null}이다 — 조직/서명자 신원은
         * 노출하지 않고 "이 파일이 진짜 결과물인지"만 확인해준다(signstage-docs
         * business/ceremony-feature-migration-review.md §2.5).
         */
        @Getter
        @AllArgsConstructor
        public static class VerificationResult {

            private final boolean verified;
            private final String resultType;
            private final String ceremonyTitle;
            private final String eventName;
            private final LocalDateTime generatedAt;
            private final LocalDateTime verifiedAt;
        }
    }
}
