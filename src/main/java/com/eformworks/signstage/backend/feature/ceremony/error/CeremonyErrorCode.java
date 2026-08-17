package com.eformworks.signstage.backend.feature.ceremony.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * feature.ceremony 업무 오류. 1라운드(과금 카탈로그) 코드에 2라운드(Ceremony/CeremonyEvent 본체)
 * 코드를 추가했다. 접근 권한 실패는 별도 코드를 두지 않고 기존 MemberService 관례대로
 * {@code CommonErrorCode.ACCESS_DENIED}를 재사용한다.
 */
public enum CeremonyErrorCode implements ErrorCode {

    BILLING_PLAN_NOT_FOUND("CEREMONY_BILLING_PLAN_NOT_FOUND", HttpStatus.NOT_FOUND, "과금 플랜을 찾을 수 없습니다."),
    OPTIONAL_FEATURE_NOT_FOUND("CEREMONY_OPTIONAL_FEATURE_NOT_FOUND", HttpStatus.NOT_FOUND, "선택옵션을 찾을 수 없습니다."),
    CAPACITY_ADDON_NOT_FOUND("CEREMONY_CAPACITY_ADDON_NOT_FOUND", HttpStatus.NOT_FOUND, "용량 추가구매 상품을 찾을 수 없습니다."),
    OPTIONAL_FEATURE_CODE_DUPLICATE(
            "CEREMONY_OPTIONAL_FEATURE_CODE_DUPLICATE",
            HttpStatus.CONFLICT,
            "이미 등록된 선택옵션 코드입니다."
    ),
    CEREMONY_NOT_FOUND("CEREMONY_NOT_FOUND", HttpStatus.NOT_FOUND, "행사를 찾을 수 없습니다."),
    CEREMONY_EVENT_NOT_FOUND("CEREMONY_EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "하위 행사를 찾을 수 없습니다."),
    CEREMONY_EVENT_LIMIT_EXCEEDED(
            "CEREMONY_EVENT_LIMIT_EXCEEDED",
            HttpStatus.CONFLICT,
            "이 유형의 하위 행사 생성 한도를 초과했습니다. 플랜을 올리거나 용량을 추가구매해주세요."
    ),
    OPTIONAL_FEATURE_NOT_PURCHASED(
            "CEREMONY_OPTIONAL_FEATURE_NOT_PURCHASED",
            HttpStatus.CONFLICT,
            "구매하지 않은 선택옵션은 하위 행사에 적용할 수 없습니다."
    ),
    OPTIONAL_FEATURE_ALREADY_PURCHASED(
            "CEREMONY_OPTIONAL_FEATURE_ALREADY_PURCHASED",
            HttpStatus.CONFLICT,
            "이미 구매한 선택옵션입니다."
    ),
    SIGNER_NOT_FOUND("CEREMONY_SIGNER_NOT_FOUND", HttpStatus.NOT_FOUND, "서명자를 찾을 수 없습니다."),
    CEREMONY_SIGNER_LIMIT_EXCEEDED(
            "CEREMONY_SIGNER_LIMIT_EXCEEDED",
            HttpStatus.CONFLICT,
            "서명자 등록 한도를 초과했습니다. 플랜을 올리거나 용량을 추가구매해주세요."
    ),
    TEMPLATE_NOT_FOUND("CEREMONY_TEMPLATE_NOT_FOUND", HttpStatus.NOT_FOUND, "템플릿을 찾을 수 없습니다."),
    CEREMONY_TEMPLATE_LIMIT_EXCEEDED(
            "CEREMONY_TEMPLATE_LIMIT_EXCEEDED",
            HttpStatus.CONFLICT,
            "템플릿 업로드 한도를 초과했습니다. 플랜을 올리거나 용량을 추가구매해주세요."
    ),
    TEMPLATE_FIELD_NOT_FOUND("CEREMONY_TEMPLATE_FIELD_NOT_FOUND", HttpStatus.NOT_FOUND, "서명란을 찾을 수 없습니다."),
    TEMPLATE_STORAGE_FAILED(
            "CEREMONY_TEMPLATE_STORAGE_FAILED",
            HttpStatus.BAD_GATEWAY,
            "문서 파일 저장에 실패했습니다."
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
