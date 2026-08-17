package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * CeremonyEventLog의 행위 종류. 이번 라운드에서 실제로 쓰는 값만 우선 정의한다 —
 * GENERATE_RESULTS/SIGNATURE_CLEAR/SIGNATURE_REPLACE는 다음 라운드에 추가한다.
 */
public enum CeremonyEventAction {
    START_EVENT,
    FINISH_EVENT,
    SIGNATURE_COMPLETE
}
