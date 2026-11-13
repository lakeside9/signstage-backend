package com.eformworks.signstage.backend.feature.order.entity;

/** 고객 취소 보상 상태 — 생기면 PENDING, 정산 지급 건에 더해지면 APPLIED(지급 건을 취소하면 PENDING으로 돌아간다). */
public enum PartnerCompensationStatus {
    PENDING,
    APPLIED
}
