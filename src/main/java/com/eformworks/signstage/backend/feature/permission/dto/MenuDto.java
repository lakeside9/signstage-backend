package com.eformworks.signstage.backend.feature.permission.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class MenuDto {

    private MenuDto() {
    }

    public static final class Request {

        private Request() {
        }

        /** 12장 결정 #10(2026-09-05) — 이름/경로/순서까지 관리 화면에서 편집 가능하다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateMenu {

            /** 생략하면(null) 현재 언어 라벨을 바꾸지 않는다. */
            private String label;

            private String path;

            private String iconKey;

            @NotNull
            private Integer displayOrder;

            @NotNull
            private Boolean active;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class MenuNode {
            private Long id;
            private String menuKey;
            private String labelKey;
            /** 호출자의 Accept-Language에 맞춰 해석된 표시명 — menu_translations 우선, 없으면 messageKey 번역. */
            private String label;
            private String path;
            private String iconKey;
            private int displayOrder;
            private List<MenuNode> children;
        }

        /**
         * 메뉴 관리 화면(12장 결정 #10) 전용 — 역할 필터링 없이 평면 목록으로 내려준다.
         * {@code parentMenuId}로 프런트가 계층을 재구성한다.
         */
        @Getter
        @AllArgsConstructor
        public static class MenuAdminRow {
            private Long id;
            private Long parentMenuId;
            private String menuKey;
            private String labelKey;
            private String label;
            private String path;
            private String iconKey;
            private int displayOrder;
            private boolean active;
        }
    }
}
