package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * {@link OrganizationSubscription} 상태값 8종 — signstage-docs
 * business/organization-event-discount-pricing-review.md 8.3-1/8.6-A-3/8.7절 결정
 * (2026-09-10 착수 확정). {@code PERIOD_AND_COUNT} 구독은 {@link #EXPIRED}/{@link #EXHAUSTED}
 * 둘 다 도달 가능(먼저 온 사건이 실제 종료 상태를 결정), {@code COUNT_ONLY} 구독은
 * {@link #EXHAUSTED}만 도달 가능하다(기간이 없어 만료가 원천적으로 없음).
 */
public enum OrganizationSubscriptionStatus {
    /** 조직(OWNER)이 요청했고 플랫폼 관리자 승인을 기다리는 중. */
    PENDING,
    /** 승인돼 실제로 사용 중. */
    ACTIVE,
    /** ACTIVE 상태에서 조직(OWNER)이 중도 해지를 요청해 관리자 승인을 기다리는 중. */
    CANCELLATION_REQUESTED,
    /** 기간이 만료돼 종료됨(PERIOD_AND_COUNT만 도달). */
    EXPIRED,
    /** 허용 횟수를 모두 소진해 종료됨. */
    EXHAUSTED,
    /** 중도 해지 요청이 관리자 승인을 받아 종료됨. */
    CANCELLED,
    /** 같은 조직이 재계약(새 구독 요청·승인)해 이 구독이 대체됨. */
    SUPERSEDED,
    /** 요청이 반려됨. */
    REJECTED
}
