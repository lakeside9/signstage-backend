package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 업로드는 multipart(title/documentRole/file)라 별도 Request DTO 없이 컨트롤러에서
 * {@code @RequestParam}으로 받는다(파일 업로드 표준 패턴). 수정은 JSON이라 별도 DTO를 둔다.
 */
public final class TemplateDto {

    private TemplateDto() {
    }

    public static final class Request {

        private Request() {
        }

        /** 제목/문서유형만 바꾼다. PDF 파일은 여기서 바꾸지 않는다(서명란 좌표가 깨지기 때문). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateTemplate {

            @NotBlank
            private String title;

            @NotBlank
            private String documentRole;
        }
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
            /** 목록 화면의 표시 순서 — 위/아래 이동 버튼이 이 값을 그대로 다시 인덱싱해 저장한다. */
            private final Integer displayOrder;
            /** 서명란(TemplateField) 개수 — status 계산과 목록 화면 "서명란" 컬럼에 쓴다. */
            private final long fieldCount;
            /** 시작/종료된 하위 행사에 매핑돼 수정이 막힌 문서 양식인지 — true면 화면에서 수정 버튼을 비활성화한다. */
            private final boolean locked;
            /** 하위 행사에 매핑돼 있어 삭제할 수 없는 문서 양식이면 false — 화면에서 삭제 버튼을 숨긴다. */
            private final boolean deletable;
            private final LocalDateTime createdAt;
        }

        /**
         * 서명란 배치 화면이 캔버스 크기를 잡는 데 쓴다(PDF 첫 페이지 크기 기준, pt 단위).
         * {@code pages}는 페이지별 CropBox/회전을 반영한 실제 표시 크기다 — 페이지마다 용지
         * 방향이 다른 문서(가로 페이지가 섞인 경우 등)를 위한 것으로, 첫 페이지 크기만
         * 참조하던 기존 화면은 {@code width}/{@code height}를 그대로 쓰면 된다.
         */
        @Getter
        @AllArgsConstructor
        public static class TemplateInfo {

            private final int pageCount;
            private final Float width;
            private final Float height;
            private final List<TemplatePageInfo> pages;
        }

        /** 회전(rotation)까지 반영해 실제로 화면에 표시될 페이지 크기(pt)로 뒤집어 놓은 값이다. */
        @Getter
        @AllArgsConstructor
        public static class TemplatePageInfo {

            private final int pageIndex;
            private final float width;
            private final float height;
            private final int rotation;
        }
    }
}
