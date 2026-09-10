package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * {@link OrganizationSubscription} 승인 주체 — signstage-docs
 * business/organization-event-discount-pricing-review.md 8.4-8/8.7절 결정(2026-09-10).
 * 지금은 항상 {@link #MANUAL}이다(플랫폼 관리자가 직접 승인) — 결제 시스템이 추가되면
 * 결제 완료 웹훅이 {@link #PAYMENT}로 자동 승인하도록 확장할 자리를 미리 마련해둔다. 실제
 * 결제 연동은 이 구독 기능의 범위 밖이다.
 */
public enum SubscriptionApprovalSource {
    MANUAL,
    PAYMENT
}
