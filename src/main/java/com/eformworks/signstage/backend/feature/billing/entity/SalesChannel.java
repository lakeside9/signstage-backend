package com.eformworks.signstage.backend.feature.billing.entity;

/**
 * 단위 상품·과금 플랜의 판매 경로 — signstage-docs
 * business/platform-order-unit-product-composition-review.md U01(2026-11-12 결정). 두 경로의
 * 카탈로그는 섞지 않는다. 플랜에는 같은 경로의 단위 상품만 넣고, 등록한 뒤에는 경로를 바꾸지 않는다.
 */
public enum SalesChannel {
    /**
     * 파트너 직접 수주(D01로 보류) — 파트너가 플랜·추가구매로 시스템 이용료를 사고, 장비·인력은 고객 견적에
     * 직접 넣는다. 가격은 단위 상품 판매 가격 기간·플랜 할인 기간에서 온다.
     */
    PARTNER_DIRECT,
    /**
     * 플랫폼 수주·파트너 발주 — 플랜은 제공 범위만 정하고, 가격은 고객 가격표·파트너 단가표에서 온다.
     * 판매 가격 기간·플랜 할인 기간을 두지 않는다(U05).
     */
    PLATFORM_ORDER
}
