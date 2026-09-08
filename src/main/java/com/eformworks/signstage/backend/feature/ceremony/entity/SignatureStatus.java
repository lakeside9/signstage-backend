package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 행사 이벤트 안에서 서명자 한 명의 "지금" 서명 상태 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-STATE. 감사 로그
 * (SIGNATURE_COMPLETE/REPLACE/CLEAR)를 매번 재조회해 판정하던 것을 이 단일 상태 컬럼으로
 * 대체한다(V202609071000의 {@code ceremony_event_signer_states.signature_status} CHECK 제약과
 * 값이 같다).
 */
public enum SignatureStatus {
    PENDING,
    SIGNING,
    COMPLETED
}
