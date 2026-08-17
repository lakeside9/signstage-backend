package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 필수옵션(용량 한도) 추가구매 상품이 어느 한도를 늘리는지 구분한다.
 * signstage-docs business/ceremony-billing-options-review.md 4.7절(CapacityAddOn) 참고.
 */
public enum CapacityType {
    SIGNERS,
    TEMPLATES,
    TEST_EVENTS,
    MAIN_EVENTS
}
