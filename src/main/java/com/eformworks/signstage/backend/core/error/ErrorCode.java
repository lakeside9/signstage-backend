package com.eformworks.signstage.backend.core.error;

import org.springframework.http.HttpStatus;

import java.util.Locale;

public interface ErrorCode {
    String getCode();
    HttpStatus getHttpStatus();
    String getMessage();

    /**
     * API와 번역 리소스에서 공통으로 사용하는 안정적인 메시지 키다.
     * 기존 enum을 한 번에 마이그레이션할 수 있도록 오류 코드에서 기본 키를 유도한다.
     */
    default String getMessageKey() {
        return "error." + getCode().toLowerCase(Locale.ROOT).replace('_', '.');
    }
}
