package com.eformworks.signstage.backend.feature.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class PermissionDto {

    private PermissionDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SetAllowed {

            @NotBlank
            private String roleValue;

            @NotNull
            private Boolean allowed;
        }
    }

    public static final class Response {

        private Response() {
        }

        /** 로그인한 역할이 가진 허용 권한키 집합 — 10장, 프런트 hasPermission(key)이 참조한다. */
        @Getter
        @AllArgsConstructor
        public static class MyPermissions {
            private String roleAxis;
            private String roleValue;
            private List<String> permissionKeys;
        }

        /** 관리 화면의 "역할 × 권한키" 매트릭스 한 행. */
        @Getter
        @AllArgsConstructor
        public static class PermissionMatrixRow {
            private Long permissionDefinitionId;
            private String permissionKey;
            private String permissionType;
            private String labelKey;
            private int displayOrder;
            private List<RoleAllowance> roleAllowances;
        }

        @Getter
        @AllArgsConstructor
        public static class RoleAllowance {
            private String roleValue;
            private boolean allowed;
        }
    }
}
