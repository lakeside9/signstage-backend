package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * CeremonyEventLog의 행위 종류. "지금 완료 상태인가"는 더 이상 이 로그를 최신순으로 훑어
 * 판정하지 않는다 — {@code ceremony_event_signer_states}(BE-STATE,
 * {@code CeremonyEventSignerStateService}) 단일 상태 테이블이 그 역할을 맡고, 이 로그는
 * append-only 감사 기록으로만 남는다.
 */
public enum CeremonyEventAction {
    START_EVENT,
    FINISH_EVENT,
    /** TEST/REHEARSAL 행사를 서명 완료 여부와 무관하게 강제로 끝냈을 때 남긴다(2026-08-27 포팅). */
    FORCE_FINISH_EVENT,
    SIGNATURE_COMPLETE,
    SIGNATURE_CLEAR,
    SIGNATURE_REPLACE,
    GENERATE_RESULTS,
    /** 전원완료 효과가 서버에 의해 자동으로 처음 한 번 실행됐을 때 남긴다(BE-RUNTIME-02). */
    EFFECT_AUTO_TRIGGERED,
    /** 관리자가 전체 효과를 수동으로 실행했을 때 남긴다(BE-RUNTIME-04). */
    EFFECT_MANUAL_TRIGGERED,
    /** 관리자가 행사 진행 중 효과 runtime ON/OFF를 바꿨을 때 남긴다(BE-RUNTIME-03). */
    EFFECT_RUNTIME_CHANGED
}
