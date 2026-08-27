package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 필수옵션(용량 한도) 추가구매 상품이 어느 한도를 늘리는지 구분한다.
 * signstage-docs business/ceremony-billing-options-review.md 4.7절(CapacityAddOn) 참고.
 */
public enum CapacityType {
    SIGNERS,
    TEMPLATES,
    TEST_EVENTS,
    /** 하위 행사 REHEARSAL 구분의 한도(2026-08-27 legacy 포팅) — TEST와는 별도 버킷이다. */
    REHEARSAL_EVENTS,
    MAIN_EVENTS,
    /**
     * 태블릿 대여 대수. 다른 값과 달리 {@code BillingPlan}에 대응하는 필수 필드(기본 포함 대수)가
     * 없다 — 플랜 기본 포함 없이 항상 0에서 시작해 {@link CapacityAddOn} 추가구매로만 늘어난다
     * (signstage-docs business/ceremony-billing-options-review.md 4.7절 후속, 2026-08-21).
     */
    TABLETS
}
