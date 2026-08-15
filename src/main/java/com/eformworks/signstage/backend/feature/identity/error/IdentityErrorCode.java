package com.eformworks.signstage.backend.feature.identity.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * signstage-docs business/login-security.md 4.3절의 오류 코드 설계를 따른다.
 * FAILED_NOT_FOUND/FAILED_INVALID_PASSWORD/FAILED_WITHDRAWN은 계정 열거(enumeration) 공격을
 * 막기 위해 모두 INVALID_CREDENTIAL 하나로 응답한다(login_history에는 상세 사유가 그대로 남는다).
 */
public enum IdentityErrorCode implements ErrorCode {

    INVALID_CREDENTIAL("IDENTITY_INVALID_CREDENTIAL", HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_LOCKED("IDENTITY_ACCOUNT_LOCKED", HttpStatus.LOCKED, "계정이 잠겼습니다. 잠시 후 다시 시도해주세요."),
    ACCOUNT_PENDING_APPROVAL(
            "IDENTITY_ACCOUNT_PENDING_APPROVAL",
            HttpStatus.FORBIDDEN,
            "가입 승인 대기 중입니다. 승인 후 로그인할 수 있습니다."
    ),
    ACCOUNT_DISABLED("IDENTITY_ACCOUNT_DISABLED", HttpStatus.FORBIDDEN, "비활성화된 계정입니다. 관리자에게 문의하세요."),
    DUPLICATE_LOGIN_ID("IDENTITY_DUPLICATE_LOGIN_ID", HttpStatus.CONFLICT, "이미 존재하는 아이디입니다."),
    DUPLICATE_EMAIL("IDENTITY_DUPLICATE_EMAIL", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_RESET_TOKEN("IDENTITY_INVALID_RESET_TOKEN", HttpStatus.UNAUTHORIZED, "비밀번호 변경 요청이 유효하지 않습니다. 다시 로그인해주세요.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    IdentityErrorCode(String code, HttpStatus httpStatus, String message) {
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
