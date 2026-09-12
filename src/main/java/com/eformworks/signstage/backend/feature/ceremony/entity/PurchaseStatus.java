package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 용량/선택옵션 추가구매 요청의 승인 상태. {@link CeremonyCapacityPurchase}와
 * {@link CeremonyOptionalFeaturePurchase}가 공유한다(값 구성이 같아서 {@link DiscountType}처럼
 * 공유 enum으로 둔다). 요청 즉시 PENDING으로 생기고, 플랫폼 관리자가 승인(APPROVED)해야
 * 유효 한도/구매한 선택옵션 집계에 반영된다 — signstage-docs
 * business/ceremony-billing-options-review.md 참고.
 *
 * <p>{@code CANCELLED}는 이미 승인(APPROVED)된 구매를 관리자가 나중에 취소한 것 —
 * signstage-docs business/ceremony-unit-product-purchase-cancellation-review.md 결정
 * (2026-09-12). {@code REJECTED}(승인 자체가 안 됨)와 의미가 달라 재사용하지 않고 별도
 * 값으로 뒀다. {@code PENDING}/{@code APPROVED}만 열거하는 기존 필터
 * (예: {@code CeremonyService#checkPurchaseQuantity})에는 자연히 포함되지 않는다
 * (REJECTED와 같은 원리).
 */
public enum PurchaseStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
