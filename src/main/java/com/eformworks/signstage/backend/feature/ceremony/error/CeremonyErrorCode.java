package com.eformworks.signstage.backend.feature.ceremony.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * feature.ceremony 업무 오류. 1라운드(과금 카탈로그)에서 실제로 쓰는 코드만 우선 정의하고,
 * 2라운드(Ceremony/CeremonyEvent 본체)에서 CEREMONY_NOT_FOUND, CEREMONY_PLAN_REQUIRED,
 * CEREMONY_*_LIMIT_EXCEEDED 등을 추가한다.
 */
public enum CeremonyErrorCode implements ErrorCode {

    BILLING_PLAN_NOT_FOUND("CEREMONY_BILLING_PLAN_NOT_FOUND", HttpStatus.NOT_FOUND, "과금 플랜을 찾을 수 없습니다."),
    OPTIONAL_FEATURE_NOT_FOUND("CEREMONY_OPTIONAL_FEATURE_NOT_FOUND", HttpStatus.NOT_FOUND, "선택옵션을 찾을 수 없습니다."),
    CAPACITY_ADDON_NOT_FOUND("CEREMONY_CAPACITY_ADDON_NOT_FOUND", HttpStatus.NOT_FOUND, "용량 추가구매 상품을 찾을 수 없습니다."),
    OPTIONAL_FEATURE_CODE_DUPLICATE(
            "CEREMONY_OPTIONAL_FEATURE_CODE_DUPLICATE",
            HttpStatus.CONFLICT,
            "이미 등록된 선택옵션 코드입니다."
    );

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    CeremonyErrorCode(String code, HttpStatus httpStatus, String message) {
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
