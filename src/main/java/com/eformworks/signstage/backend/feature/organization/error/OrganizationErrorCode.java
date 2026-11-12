package com.eformworks.signstage.backend.feature.organization.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * signstage-docs business/user-organization-design.md 3~4장의 조직/멤버 정책을 따른다.
 */
public enum OrganizationErrorCode implements ErrorCode {

    ORGANIZATION_CODE_DUPLICATE("ORGANIZATION_CODE_DUPLICATE", HttpStatus.CONFLICT, "이미 사용 중인 조직 코드입니다."),
    ORGANIZATION_NOT_FOUND("ORGANIZATION_NOT_FOUND", HttpStatus.NOT_FOUND, "조직을 찾을 수 없습니다."),
    ORGANIZATION_MEMBER_NOT_FOUND("ORGANIZATION_MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND, "조직 멤버를 찾을 수 없습니다."),
    ORGANIZATION_MEMBER_USER_NOT_FOUND(
            "ORGANIZATION_MEMBER_USER_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "추가하려는 사용자를 찾을 수 없습니다."
    ),
    ORGANIZATION_MEMBER_ALREADY_EXISTS(
            "ORGANIZATION_MEMBER_ALREADY_EXISTS",
            HttpStatus.CONFLICT,
            "이미 조직에 속한 사용자입니다."
    ),
    ORGANIZATION_ONLY_OWNER_CAN_ASSIGN_OWNER(
            "ORGANIZATION_ONLY_OWNER_CAN_ASSIGN_OWNER",
            HttpStatus.FORBIDDEN,
            "OWNER 역할은 OWNER만 지정하거나 변경할 수 있습니다."
    ),
    ORGANIZATION_ONLY_OWNER_CAN_REMOVE_OWNER(
            "ORGANIZATION_ONLY_OWNER_CAN_REMOVE_OWNER",
            HttpStatus.FORBIDDEN,
            "OWNER는 OWNER만 제거할 수 있습니다."
    ),
    ORGANIZATION_LAST_OWNER_REQUIRED(
            "ORGANIZATION_LAST_OWNER_REQUIRED",
            HttpStatus.CONFLICT,
            "조직에는 항상 최소 1명의 OWNER가 있어야 합니다."
    ),
    ORGANIZATION_REQUEST_NOT_FOUND(
            "ORGANIZATION_REQUEST_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "조직 생성 요청을 찾을 수 없습니다."
    ),
    ORGANIZATION_REQUEST_ALREADY_PENDING(
            "ORGANIZATION_REQUEST_ALREADY_PENDING",
            HttpStatus.CONFLICT,
            "이미 심사 대기 중인 조직 생성 요청이 있습니다."
    ),
    ORGANIZATION_REQUEST_LIMIT_EXCEEDED(
            "ORGANIZATION_REQUEST_LIMIT_EXCEEDED",
            HttpStatus.CONFLICT,
            "조직 생성 요청은 최대 5회까지 제출할 수 있습니다."
    ),
    ORGANIZATION_REQUEST_NOT_PENDING(
            "ORGANIZATION_REQUEST_NOT_PENDING",
            HttpStatus.CONFLICT,
            "심사 대기 중인 요청만 처리할 수 있습니다."
    ),
    ORGANIZATION_OWNER_LIMIT_EXCEEDED(
            "ORGANIZATION_OWNER_LIMIT_EXCEEDED",
            HttpStatus.CONFLICT,
            "한 사용자가 OWNER로 보유할 수 있는 조직은 최대 10개입니다."
    ),
    ORGANIZATION_SINGLE_MEMBERSHIP_LIMIT(
            "ORGANIZATION_SINGLE_MEMBERSHIP_LIMIT",
            HttpStatus.CONFLICT,
            "한 사용자는 동시에 하나의 조직에만 속할 수 있습니다."
    ),
    ORGANIZATION_MEMBER_IS_PLATFORM_ADMIN(
            "ORGANIZATION_MEMBER_IS_PLATFORM_ADMIN",
            HttpStatus.CONFLICT,
            "플랫폼 관리자는 조직에 소속될 수 없습니다."
    ),
    ORGANIZATION_PARTNER_PROFILE_NOT_FOUND(
            "ORGANIZATION_PARTNER_PROFILE_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "파트너 외주 정보가 등록되지 않았습니다."
    ),
    ORGANIZATION_PARTNER_BUSINESS_NUMBER_REQUIRED(
            "ORGANIZATION_PARTNER_BUSINESS_NUMBER_REQUIRED",
            HttpStatus.BAD_REQUEST,
            "개인이 아닌 사업자는 사업자등록번호를 입력해야 합니다."
    ),
    ORGANIZATION_PARTNER_ACCOUNT_NOT_REGISTERED(
            "ORGANIZATION_PARTNER_ACCOUNT_NOT_REGISTERED",
            HttpStatus.CONFLICT,
            "정산 계좌(은행·계좌번호·예금주)가 모두 등록되지 않아 확인할 수 없습니다."
    );

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    OrganizationErrorCode(String code, HttpStatus httpStatus, String message) {
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
