package com.eformworks.signstage.backend.feature.ceremony.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 데모 체험 계정(VIEWER)용 시나리오 목록 — signstage-docs
 * business/demo-account-exhibition-signer-preview-review.md 4.2/5.4절 결정(2026-09-10 구현).
 * {@code GET /api/demo-viewer/scenarios} 응답이며, {@code DemoView}가 이 목록으로 항목을
 * 그리고 각 항목의 두 accessKey로 전시용/서명자용 화면 링크를 연다.
 */
public final class DemoScenarioDto {

    private DemoScenarioDto() {
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class DemoScenario {

            private final Long ceremonyEventId;
            /** 목록 표시 이름 — {@code Ceremony.title}. */
            private final String title;
            private final String eventAccessKey;
            /** 이 이벤트에 등록된 첫 서명자의 접속키(데모는 서명자 1명을 전제로 등록한다, 5.1절). */
            private final String signerAccessKey;
        }
    }
}
