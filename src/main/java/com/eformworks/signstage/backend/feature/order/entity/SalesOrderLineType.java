package com.eformworks.signstage.backend.feature.order.entity;

public enum SalesOrderLineType {
    /** 패키지(플랜) 가격 */
    BILLING_PLAN,
    /** 추가 옵션(단위 상품) */
    UNIT_PRODUCT,
    REGION_SURCHARGE,
    /** 영업 할인 — 금액이 음수다 */
    DISCOUNT
}
