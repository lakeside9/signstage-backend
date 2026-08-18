package com.eformworks.signstage.backend.feature.ceremony.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 행사제어/프로젝터 화면이 실시간 스트로크를 캐치업 조회할 때 쓴다
 * ({@code GET .../events/{eventId}/strokes}, {@code GET /api/projector/events/{eventAccessKey}/strokes}).
 * 관리자 콘솔용과 공개 프로젝터용이 모양이 같아 하나로 공유한다 — 어차피 두 경로 모두
 * 같은 이벤트의 스트로크를 그대로 보여주는 용도라 별도로 가릴 정보가 없다.
 */
public final class StrokeDataDto {

    private StrokeDataDto() {
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class StrokeSummary {

            private final Long id;
            private final Long signerId;
            private final Long templateFieldId;
            private final Integer strokeSeq;
            private final String rawData;
            private final LocalDateTime createdAt;
        }
    }
}
