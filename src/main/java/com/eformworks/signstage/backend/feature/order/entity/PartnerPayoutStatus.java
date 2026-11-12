package com.eformworks.signstage.backend.feature.order.entity;

/** 월 합산 지급 상태 — 정산을 만들면 CONFIRMED, 이체 후 PAID. 이체 전에는 취소(CANCELLED)해 검수 건을 풀 수 있다. */
public enum PartnerPayoutStatus {
    CONFIRMED,
    PAID,
    CANCELLED
}
