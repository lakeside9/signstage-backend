package com.eformworks.signstage.backend.feature.demo.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DemoErrorCode implements ErrorCode {

    CONFIG_NOT_FOUND("DEMO_CONFIG_NOT_FOUND", HttpStatus.NOT_FOUND, "데모 프로필을 찾을 수 없습니다."),
    EVENT_NOT_DEMO_EVENT(
            "DEMO_EVENT_NOT_DEMO_EVENT",
            HttpStatus.CONFLICT,
            "데모 조직 소속 행사만 데모 프로필에 지정할 수 있습니다."
    );

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    DemoErrorCode(String code, HttpStatus httpStatus, String message) {
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
