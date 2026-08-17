package com.eformworks.signstage.backend.feature.ceremony.model;

/**
 * 파일 저장 결과 값 객체. JPA와 무관한 순수 도메인 객체라 model 패키지에 둔다(DTO가 아니므로
 * backend-coding-convention.md 6.6절의 "record는 DTO에 안 쓴다" 제약과 무관 — {@code CurrentUser}와
 * 같은 값 객체 용도).
 */
public record StoredFile(String storageKey, String storedFilename) {
}
