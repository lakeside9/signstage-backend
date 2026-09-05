package com.eformworks.signstage.backend.feature.permission.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * feature.permission(메뉴/역할 권한 관리) 업무 오류. signstage-docs
 * business/menu-and-action-permission-management-review.md 참고.
 */
public enum PermissionErrorCode implements ErrorCode {

    MENU_NOT_FOUND("PERMISSION_MENU_NOT_FOUND", HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."),
    PERMISSION_DEFINITION_NOT_FOUND(
            "PERMISSION_DEFINITION_NOT_FOUND", HttpStatus.NOT_FOUND, "권한 항목을 찾을 수 없습니다."
    ),
    ROLE_PERMISSION_NOT_FOUND(
            "PERMISSION_ROLE_PERMISSION_NOT_FOUND", HttpStatus.NOT_FOUND,
            "해당 역할에 대한 권한 설정을 찾을 수 없습니다."
    ),
    ROLE_VALUE_INVALID(
            "PERMISSION_ROLE_VALUE_INVALID", HttpStatus.BAD_REQUEST,
            "권한 축과 역할값이 일치하지 않습니다."
    );

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    PermissionErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
