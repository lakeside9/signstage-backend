package com.eformworks.signstage.backend.feature.ceremony.entity;

/** 하위 행사(CeremonyEvent) 구분. TEST/MAIN은 같은 Ceremony 아래에서 Signer/Template를 공유한다. */
public enum CeremonyEventType {
    TEST,
    MAIN
}
