package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 결과물 종류. {@code AUDIT_TRAIL}(감사 인증서)은 서명 완결 문서 렌더링과 다른 별도 생성
 * 로직이 필요해 이번 라운드에는 없다.
 */
public enum CeremonyResultType {
    CONTRACT,
    EXHIBITION
}
