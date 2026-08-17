package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 할인 표현 방식. signstage-docs business/ceremony-billing-options-review.md 4.7절 결정 —
 * 품목 할인/최종 합계 할인 모두 퍼센트·정액 두 방식을 지원한다.
 */
public enum DiscountType {
    PERCENT,
    FIXED_AMOUNT
}
