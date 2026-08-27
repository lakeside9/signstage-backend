package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 하위 행사(CeremonyEvent) 구분. TEST/REHEARSAL/MAIN은 같은 Ceremony 아래에서 Signer/Template를
 * 공유한다. REHEARSAL은 legacy(~/Works/eform/source/signstage) 2026-08-27 포팅으로 추가됐고,
 * 과금 용량 한도는 TEST와 별도인 자기 버킷(CapacityType.REHEARSAL_EVENTS)을 쓴다 —
 * BillingPlan.maxRehearsalEvents로 카탈로그가 독립적으로 관리한다.
 */
public enum CeremonyEventType {
    TEST,
    REHEARSAL,
    MAIN
}
