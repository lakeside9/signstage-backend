package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 하위 행사(CeremonyEvent) 구분. TEST/REHEARSAL/MAIN은 같은 Ceremony 아래에서 Signer/Template를
 * 공유한다. REHEARSAL은 legacy(~/Works/eform/source/signstage) 2026-08-27 포팅으로 추가됐다 —
 * 과금 용량 한도는 TEST와 같은 버킷(CapacityType.TEST_EVENTS)을 공유한다(별도 카탈로그 한도를
 * 새로 만들지 않기로 한 판단, signstage-docs business/ceremony-feature-migration-review.md
 * 최신 라운드 참고).
 */
public enum CeremonyEventType {
    TEST,
    REHEARSAL,
    MAIN
}
