package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 용량/선택옵션 추가구매 요청의 승인 상태. {@link CeremonyCapacityPurchase}와
 * {@link CeremonyOptionalFeaturePurchase}가 공유한다(값 구성이 같아서 {@link DiscountType}처럼
 * 공유 enum으로 둔다). 요청 즉시 PENDING으로 생기고, 플랫폼 관리자가 승인(APPROVED)해야
 * 유효 한도/구매한 선택옵션 집계에 반영된다 — signstage-docs
 * business/ceremony-billing-options-review.md 참고.
 */
public enum PurchaseStatus {
    PENDING,
    APPROVED,
    REJECTED
}
