package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * CeremonyEventLog의 행위 종류. {@code SIGNATURE_COMPLETE}/{@code SIGNATURE_REPLACE} 중 어느
 * 쪽이 더 최근 로그인지로 "지금 완료 상태인가"를 판정한다({@code SignerPortalService},
 * {@code CeremonyEventService} — append-only 로그라 값을 고치지 않고 최신 행위로 판정한다).
 */
public enum CeremonyEventAction {
    START_EVENT,
    FINISH_EVENT,
    /** TEST/REHEARSAL 행사를 서명 완료 여부와 무관하게 강제로 끝냈을 때 남긴다(2026-08-27 포팅). */
    FORCE_FINISH_EVENT,
    SIGNATURE_COMPLETE,
    SIGNATURE_CLEAR,
    SIGNATURE_REPLACE,
    GENERATE_RESULTS
}
