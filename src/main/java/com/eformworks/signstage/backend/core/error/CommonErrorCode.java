package com.eformworks.signstage.backend.core.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonErrorCode implements ErrorCode {

    INVALID_REQUEST("INVALID_REQUEST", HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED("ACCESS_DENIED", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    /**
     * DB 제약(유니크·외래키 등) 위반으로 저장에 실패했을 때 쓴다 —
     * {@code GlobalExceptionHandler#handleDataIntegrityViolationException}이
     * {@code DataIntegrityViolationException}을 여기로 번역한다. 이전엔 이 예외가 잡히지 않아
     * {@link #INTERNAL_SERVER_ERROR}(500, "서버 오류가 발생했습니다.")로 뭉뚱그려졌었다
     * (2026-09-10, `billing_plan_unit_products.uq_bpup_plan_product` 위반 사례로 발견).
     */
    DATA_CONFLICT("DATA_CONFLICT", HttpStatus.CONFLICT, "이미 등록된 데이터와 중복되어 처리할 수 없습니다."),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    CommonErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
