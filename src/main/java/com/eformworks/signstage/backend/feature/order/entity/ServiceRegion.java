package com.eformworks.signstage.backend.feature.order.entity;

/**
 * 지역 할증 권역 — signstage-docs business/platform-order-partner-subcontract-review.md D26
 * 권장안(권역별 정액 할증). 고객 가격과 파트너 단가에 각각 할증액을 둔다.
 *
 * <p>{@link #label}은 고객 견적 줄 이름에 쓴다(견적은 고객에게 그대로 보인다).
 */
public enum ServiceRegion {
    CAPITAL("수도권"),
    CHUNGCHEONG_GANGWON("충청·강원"),
    YEONGNAM_HONAM("영남·호남"),
    JEJU_ISLAND("제주·도서");

    private final String label;

    ServiceRegion(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
