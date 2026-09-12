package com.eformworks.signstage.backend.feature.support.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * feature.support(공지사항/FAQ) 업무 오류 — signstage-docs
 * business/partner-support-center-review.md 3·4장. 행사별 1:1 문의(CeremonyInquiry)는
 * {@code Ceremony} 직속이라 {@code CeremonyErrorCode}에 코드를 둔다(Signer/Template과 같은
 * 이유).
 */
public enum SupportErrorCode implements ErrorCode {

    ANNOUNCEMENT_NOT_FOUND("SUPPORT_ANNOUNCEMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다."),
    FAQ_NOT_FOUND("SUPPORT_FAQ_NOT_FOUND", HttpStatus.NOT_FOUND, "FAQ를 찾을 수 없습니다."),
    FAQ_ORDER_GROUP_MISMATCH(
            "SUPPORT_FAQ_ORDER_GROUP_MISMATCH",
            HttpStatus.CONFLICT,
            "전달된 순서 목록이 현재 FAQ 전체 목록과 일치하지 않습니다."
    );

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    SupportErrorCode(String code, HttpStatus httpStatus, String message) {
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
