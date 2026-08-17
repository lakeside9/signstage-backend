package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * CeremonyEventLog의 행위 종류. SIGNATURE_CLEAR/SIGNATURE_REPLACE는 아직 없다(다음 라운드).
 */
public enum CeremonyEventAction {
    START_EVENT,
    FINISH_EVENT,
    SIGNATURE_COMPLETE,
    GENERATE_RESULTS
}
