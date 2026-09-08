package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 이벤트 효과가 재생되는 화면. signstage-docs
 * business/ceremony-event-effect-migration-plan.md, PRE-02절 참고. {@code SIGNER}는 스키마·계약만
 * 미리 열어두며(V202609071000), 실제 서명자 화면용 효과는 아직 구현하지 않는다.
 */
public enum CeremonyEffectTarget {
    PROJECTOR,
    SIGNER
}
