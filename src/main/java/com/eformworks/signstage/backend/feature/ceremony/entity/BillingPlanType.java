package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * {@link BillingPlan}의 종류 — signstage-docs
 * business/organization-event-discount-pricing-review.md 결정(8.1-5번, 8.5절 명명,
 * 2026-09-10 착수 확정). 대부분의 플랜은 {@link #STANDARD}다 — Ceremony 1건마다 독립적으로
 * 골라 쓰는 일반 플랜(단위 상품 묶음 + 할인, 기존 그대로)이다. {@link #SUBSCRIPTION}은
 * "N회 이용권"처럼 여러 Ceremony에 걸쳐 잔여 횟수를 세는 특수 플랜이다 —
 * {@link OrganizationSubscription}이 그 사용량을 조직 단위로 관리한다.
 *
 * <p>이 필드는 가격 계산 방식과 무관하다 — {@code SUBSCRIPTION} 플랜으로 만든 개별 Ceremony도
 * {@code STANDARD}와 똑같이 단위 상품 소계 → 플랜 할인 → 행사 건별 할인으로 정상 과금된다.
 * 구독은 순수하게 "이 조직이 이 플랜을 몇 번 더 쓸 수 있는지"를 관리하는 사용량 게이트다.
 */
public enum BillingPlanType {
    STANDARD,
    SUBSCRIPTION
}
