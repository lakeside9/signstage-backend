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
