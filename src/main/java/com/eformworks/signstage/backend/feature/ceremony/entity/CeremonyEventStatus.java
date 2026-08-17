package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 하위 행사(CeremonyEvent) 상태 전이. DRAFT→READY→STARTED→FINISHED 순서로만 진행된다
 * (signstage-docs business/ceremony-feature-migration-review.md 2.2절). 이번 라운드는
 * DRAFT 생성까지만 다루고, READY/START/FINISH 전이 API는 Template/Signer가 생기는
 * 다음 라운드에 추가한다.
 */
public enum CeremonyEventStatus {
    DRAFT,
    READY,
    STARTED,
    FINISHED
}
