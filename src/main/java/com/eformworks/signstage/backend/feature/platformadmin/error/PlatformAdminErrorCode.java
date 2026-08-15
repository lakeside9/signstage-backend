package com.eformworks.signstage.backend.feature.platformadmin.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PlatformAdminErrorCode implements ErrorCode {

    USER_NOT_FOUND("PLATFORM_ADMIN_USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    CANNOT_CHANGE_OWN_STATUS(
            "PLATFORM_ADMIN_CANNOT_CHANGE_OWN_STATUS",
            HttpStatus.FORBIDDEN,
            "본인 계정의 상태는 변경할 수 없습니다."
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
