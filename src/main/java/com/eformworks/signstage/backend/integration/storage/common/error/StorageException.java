package com.eformworks.signstage.backend.integration.storage.common.error;

/**
 * 외부/로컬 저장소 연동 실패를 감싸는 연동 예외. {@code integration}은 여기서
 * {@code ApplicationException}을 직접 던지지 않는다(backend-coding-convention.md 13.8절) —
 * feature 서비스(TemplateService)가 이 예외를 잡아 업무 오류(CeremonyErrorCode)로 변환한다.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
