package com.eformworks.signstage.backend.feature.platformadmin.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PlatformAdminErrorCode implements ErrorCode {

    USER_NOT_FOUND("PLATFORM_ADMIN_USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ORGANIZATION_NOT_FOUND("PLATFORM_ADMIN_ORGANIZATION_NOT_FOUND", HttpStatus.NOT_FOUND, "조직을 찾을 수 없습니다."),
    CANNOT_TARGET_SELF(
            "PLATFORM_ADMIN_CANNOT_TARGET_SELF",
            HttpStatus.FORBIDDEN,
            "본인 계정은 이 기능의 대상으로 지정할 수 없습니다."
    ),
    NOT_A_PLATFORM_ADMIN(
            "PLATFORM_ADMIN_NOT_A_PLATFORM_ADMIN",
            HttpStatus.CONFLICT,
            "플랫폼 관리자 권한이 없는 계정입니다."
    );

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    PlatformAdminErrorCode(String code, HttpStatus httpStatus, String message) {
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
