package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 하위 행사(CeremonyEvent) 상태 전이. DRAFT→READY→STARTED→FINISHED 순서로만 진행된다
 * (signstage-docs business/ceremony-feature-migration-review.md 2.2절).
 *
 * <p>{@code FORCE_FINISHED}는 legacy(~/Works/eform/source/signstage) 2026-08-27 포팅 — STARTED
 * 상태에서 TEST/REHEARSAL 행사만 관리자가 서명 완료 여부와 무관하게 강제로 끝낼 수 있다
 * (리허설 도중 중단 등). MAIN에는 허용하지 않는다(CeremonyEventService#forceFinishEvent). FINISHED와
 * 마찬가지로 종결 상태라 그 뒤로 다시 전이하지 않고, 문서 매핑/기본 정보 수정도 잠긴다.
 */
public enum CeremonyEventStatus {
    DRAFT,
    READY,
    STARTED,
    FINISHED,
    FORCE_FINISHED
}
