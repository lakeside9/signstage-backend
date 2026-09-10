package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * {@link BillingPlanType#SUBSCRIPTION} 플랜의 하위 구분 — signstage-docs
 * business/organization-event-discount-pricing-review.md 8.6절 A-1 결정(2026-09-05,
 * 2026-09-10 착수 확정). 기간 필드를 nullable로 두고 유추하는 대신, 유형 자체를 명시값으로
 * 저장해 종료 판정·화면 문구·검증 로직이 유형별로 갈리지 않게 한다("충돌 최소화" 요구).
 */
public enum SubscriptionType {
    /** 기간(6/12개월 중 선택)과 허용 횟수가 둘 다 필수 — "6개월 동안 5회"처럼 정기 이용권. */
    PERIOD_AND_COUNT,
    /** 기간 제한 없이 허용 횟수만 필수 — 다 쓸 때까지 유효한 횟수제 카드. */
    COUNT_ONLY
}
