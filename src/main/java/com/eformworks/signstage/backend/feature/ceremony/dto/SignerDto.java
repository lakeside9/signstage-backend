package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
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
            /** 목록 화면의 표시 순서 — 위/아래 이동 버튼이 이 값을 그대로 다시 인덱싱해 저장한다. */
            private final Integer displayOrder;
            /** 시작/종료된 하위 행사에 배정돼 수정이 막힌 서명자인지 — true면 화면에서 수정 버튼을 비활성화한다. */
            private final boolean locked;
            /** 서명란 배정/서명·감사 기록이 있어 삭제할 수 없는 서명자면 false — 화면에서 삭제 버튼을 숨긴다. */
            private final boolean deletable;
            private final LocalDateTime createdAt;
        }

        /**
         * 엑셀 일괄 업로드 결과. 이름이 비어있는 행은 등록하지 않고 {@code skippedRows}로
         * 돌려준다 — 나머지 유효한 행만 등록한다(행 하나 잘못됐다고 전체를 막지 않는다).
         * 다만 유효한 행 수가 플랜 한도를 넘으면 아무것도 등록하지 않고 하드 블록한다(4.5절과
         * 같은 원칙 — {@code CEREMONY_SIGNER_LIMIT_EXCEEDED}).
         */
        @Getter
        @AllArgsConstructor
        public static class ExcelUploadResult {

            private final List<SignerSummary> createdSigners;
            private final List<SkippedRow> skippedRows;
        }

        @Getter
        @AllArgsConstructor
        public static class SkippedRow {

            /** 엑셀의 실제 행 번호(1행=헤더, 2행부터 데이터) — 사용자가 엑셀에서 바로 찾을 수 있게. */
            private final int rowNumber;
            private final String reason;
        }
    }
}
