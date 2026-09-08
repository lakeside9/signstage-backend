package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 이벤트 효과를 재생시키는 계기. signstage-docs
 * business/ceremony-event-effect-migration-plan.md, PRE-02절 참고. {@code EVENT_FINISHED}는
 * 스키마·계약만 미리 열어두며(V202609071000), 실제 실행 로직은 BE-RUNTIME 단계에서 붙인다.
 */
public enum CeremonyEffectTrigger {
    SIGNATURE_COMPLETED,
    ALL_SIGNATURES_COMPLETED,
    EVENT_FINISHED
}
