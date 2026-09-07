package com.eformworks.signstage.backend.feature.ceremony.entity;

/** CeremonyEventLog를 남긴 주체 구분. */
public enum ActorType {
    ADMIN,
    SIGNER,
    /** 사람이 아니라 서버가 스스로 남긴 로그(예: 전원완료 자동 효과 실행) — actorId는 0L 고정. */
    SYSTEM
}
