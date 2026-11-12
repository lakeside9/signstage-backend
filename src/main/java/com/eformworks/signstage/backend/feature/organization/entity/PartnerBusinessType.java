package com.eformworks.signstage.backend.feature.organization.entity;

/**
 * 외주 수행사의 사업자 유형 — 세금계산서 발행 가능 여부와 원천징수 여부가 갈린다(signstage-docs
 * business/platform-order-partner-subcontract-review.md 7장, D16).
 */
public enum PartnerBusinessType {
    /** 일반과세자 */
    GENERAL,
    /** 간이과세자 */
    SIMPLIFIED,
    /** 사업자 등록이 없는 개인 */
    INDIVIDUAL
}
