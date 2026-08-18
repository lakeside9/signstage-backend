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
            "이미 구매를 요청했거나 승인된 선택옵션입니다."
    ),
    CAPACITY_PURCHASE_NOT_FOUND(
            "CEREMONY_CAPACITY_PURCHASE_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "용량 추가구매 요청을 찾을 수 없습니다."
    ),
    CAPACITY_PURCHASE_NOT_PENDING(
            "CEREMONY_CAPACITY_PURCHASE_NOT_PENDING",
            HttpStatus.CONFLICT,
            "이미 처리된 용량 추가구매 요청입니다."
    ),
    OPTIONAL_FEATURE_PURCHASE_NOT_FOUND(
            "CEREMONY_OPTIONAL_FEATURE_PURCHASE_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "선택옵션 추가구매 요청을 찾을 수 없습니다."
    ),
    OPTIONAL_FEATURE_PURCHASE_NOT_PENDING(
            "CEREMONY_OPTIONAL_FEATURE_PURCHASE_NOT_PENDING",
            HttpStatus.CONFLICT,
            "이미 처리된 선택옵션 추가구매 요청입니다."
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
    ),
    TEMPLATE_NOT_IN_CEREMONY(
            "CEREMONY_TEMPLATE_NOT_IN_CEREMONY",
            HttpStatus.CONFLICT,
            "이 행사에 속하지 않은 템플릿은 매핑할 수 없습니다."
    ),
    TEMPLATE_ALREADY_MAPPED(
            "CEREMONY_TEMPLATE_ALREADY_MAPPED",
            HttpStatus.CONFLICT,
            "이미 이 하위 행사에 매핑된 템플릿입니다."
    ),
    TEMPLATE_IN_USE(
            "CEREMONY_TEMPLATE_IN_USE",
            HttpStatus.CONFLICT,
            "이미 하위 행사에 매핑된 문서 양식은 삭제할 수 없습니다."
    ),
    EVENT_LOCKED(
            "CEREMONY_EVENT_LOCKED",
            HttpStatus.CONFLICT,
            "시작되었거나 종료된 하위 행사는 문서 매핑을 바꿀 수 없습니다."
    ),
    EVENT_INVALID_STATUS_TRANSITION(
            "CEREMONY_EVENT_INVALID_STATUS_TRANSITION",
            HttpStatus.CONFLICT,
            "지금 상태에서는 이 전이를 할 수 없습니다."
    ),
    EVENT_MISSING_DOCUMENT_ROLE(
            "CEREMONY_EVENT_MISSING_DOCUMENT_ROLE",
            HttpStatus.CONFLICT,
            "CONTRACT/EXHIBITION 문서가 각각 1개 이상 매핑돼야 합니다."
    ),
    EVENT_REQUIRED_FIELD_UNASSIGNED(
            "CEREMONY_EVENT_REQUIRED_FIELD_UNASSIGNED",
            HttpStatus.CONFLICT,
            "필수 서명란에 서명자가 배정되지 않았습니다."
    ),
    EVENT_SIGNER_MAPPING_MISMATCH(
            "CEREMONY_EVENT_SIGNER_MAPPING_MISMATCH",
            HttpStatus.CONFLICT,
            "CONTRACT와 EXHIBITION 문서의 필수 서명자 구성이 일치하지 않습니다."
    ),
    PORTAL_EVENT_NOT_FOUND("CEREMONY_PORTAL_EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "행사 접속 정보를 찾을 수 없습니다."),
    PORTAL_SIGNER_NOT_FOUND("CEREMONY_PORTAL_SIGNER_NOT_FOUND", HttpStatus.NOT_FOUND, "서명자 접속 정보를 찾을 수 없습니다."),
    PORTAL_FIELD_NOT_MAPPED_TO_EVENT(
            "CEREMONY_PORTAL_FIELD_NOT_MAPPED_TO_EVENT",
            HttpStatus.CONFLICT,
            "이 서명란의 문서는 이 하위 행사에 매핑돼 있지 않습니다."
    ),
    PORTAL_FIELD_NOT_ASSIGNED_TO_SIGNER(
            "CEREMONY_PORTAL_FIELD_NOT_ASSIGNED_TO_SIGNER",
            HttpStatus.FORBIDDEN,
            "본인에게 배정되지 않은 서명란입니다."
    ),
    SIGNATURE_INCOMPLETE(
            "CEREMONY_SIGNATURE_INCOMPLETE",
            HttpStatus.CONFLICT,
            "아직 서명하지 않은 필수 서명란이 있습니다."
    ),
    SIGNATURE_ALREADY_COMPLETED(
            "CEREMONY_SIGNATURE_ALREADY_COMPLETED",
            HttpStatus.CONFLICT,
            "이미 서명을 완료했습니다."
    ),
    EVENT_FINISH_CONDITION_NOT_MET(
            "CEREMONY_EVENT_FINISH_CONDITION_NOT_MET",
            HttpStatus.CONFLICT,
            "아직 서명을 완료하지 않은 서명자가 있습니다."
    ),
    EVENT_NOT_FINISHED(
            "CEREMONY_EVENT_NOT_FINISHED",
            HttpStatus.CONFLICT,
            "종료(FINISHED)된 하위 행사만 결과물을 생성할 수 있습니다."
    ),
    RESULTS_ALREADY_GENERATED(
            "CEREMONY_RESULTS_ALREADY_GENERATED",
            HttpStatus.CONFLICT,
            "이미 결과물이 생성됐습니다."
    ),
    RESULT_NOT_FOUND("CEREMONY_RESULT_NOT_FOUND", HttpStatus.NOT_FOUND, "결과물을 찾을 수 없습니다."),
    RESULT_GENERATION_FAILED(
            "CEREMONY_RESULT_GENERATION_FAILED",
            HttpStatus.BAD_GATEWAY,
            "결과물 생성에 실패했습니다."
    ),
    EVENT_NOT_IN_PROGRESS(
            "CEREMONY_EVENT_NOT_IN_PROGRESS",
            HttpStatus.CONFLICT,
            "서명 진행 중(STARTED)인 하위 행사에서만 가능합니다."
    ),
    CEREMONY_ALREADY_COMPLETED(
            "CEREMONY_ALREADY_COMPLETED",
            HttpStatus.CONFLICT,
            "완료된 행사는 더 이상 수정할 수 없습니다."
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
