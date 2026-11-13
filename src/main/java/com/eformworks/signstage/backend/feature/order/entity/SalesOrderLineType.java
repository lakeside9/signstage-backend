package com.eformworks.signstage.backend.feature.order.entity;

public enum SalesOrderLineType {
    /** 패키지(플랜) 가격 */
    BILLING_PLAN,
    /** 추가 옵션(단위 상품) */
    UNIT_PRODUCT,
    REGION_SURCHARGE,
    /** 본행사와 다른 날 리허설의 현장 인력(D24) — 현장 인력 인·일 가격 */
    REHEARSAL_STAFF,
    /** 숙박(D26) — 박수 × 인원 × 권역 1박 금액 */
    LODGING,
    /** 직접 입력 항목 — 카탈로그에 없는 품목. 고객 단가와 발주 단가를 관리자가 넣는다. */
    CUSTOM_ITEM,
    /** 영업 할인 — 금액이 음수다 */
    DISCOUNT,
    /** 공급가 단수 절사 — 버린 공급가와 그 세액이 음수다 */
    TRUNCATION
}
